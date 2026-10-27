// SPDX-License-Identifier: LGPL-3.0-only
// Copyright (C) 2026 MineAgent

package dev.dsh.killaura;

import com.example.httpd.HttpdProvider;
import com.example.httpd.PathHandler;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.minecraft.client.Minecraft;

/**
 * Kill Aura's endpoints, mounted under the shared server's {@code /ka} prefix.
 *
 * <ul>
 *   <li>{@code GET /ka/} returns the manual.</li>
 *   <li>{@code GET /ka/state} returns the current state as JSON.</li>
 *   <li>{@code POST /ka/on|off|toggle|reload|config} changes state and returns the new state.</li>
 * </ul>
 *
 * <p>The HTTP server itself (127.0.0.1:3420) belongs to MGHttpdProvider; this class only serves
 * the paths it is handed below the prefix, so {@code "/"} here means {@code /ka}.</p>
 *
 * <p>Requests arrive on the provider's HTTP worker threads, but the engine is only touched from
 * the client thread. Every read and write therefore goes through {@link #onClient(Callable)}.</p>
 */
public final class AuraEndpoint implements PathHandler {
    public static final String PREFIX = "/ka";
    public static final String NAME = "Kill Aura — 远程控制";

    /** What the provider's {@code GET /} index lists for this mod. */
    public static final List<HttpdProvider.Endpoint> ENDPOINTS = List.of(
            new HttpdProvider.Endpoint("GET", "/ka/", "使用说明"),
            new HttpdProvider.Endpoint("GET", "/ka/state", "当前状态 (JSON)"),
            new HttpdProvider.Endpoint("POST", "/ka/on", "开启"),
            new HttpdProvider.Endpoint("POST", "/ka/off", "关闭"),
            new HttpdProvider.Endpoint("POST", "/ka/toggle", "切换开关"),
            new HttpdProvider.Endpoint("POST", "/ka/reload", "重新读取 config/killaura.json"),
            new HttpdProvider.Endpoint("POST", "/ka/config", "更新配置 (JSON, 部分字段)"));

    private static final Logger LOG = Logger.getLogger("killaura");
    private static final String TEXT = "text/plain; charset=utf-8";
    private static final String JSON = "application/json; charset=utf-8";
    private static final int MAX_BODY_BYTES = 16 * 1024;
    private static final long CLIENT_TIMEOUT_MS = 5_000L;

    private final AuraEngine engine;

    public AuraEndpoint(AuraEngine engine) {
        this.engine = engine;
    }

    @Override
    public void handle(HttpExchange exchange, String path) throws IOException {
        try {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD, POST, OPTIONS");
                respond(exchange, 204, TEXT, "");
                return;
            }

            switch (path) {
                case "/", "" -> handleHelp(exchange);
                case "/state" -> handleState(exchange);
                case "/on" -> handleSet(exchange, Boolean.TRUE);
                case "/off" -> handleSet(exchange, Boolean.FALSE);
                case "/toggle" -> handleSet(exchange, null);
                case "/reload" -> handleReload(exchange);
                case "/config" -> handleConfig(exchange);
                default -> respond(exchange, 404, TEXT,
                        "no endpoint at " + PREFIX + path + "\n\n" + Help.text());
            }
        } catch (BodyTooLargeException e) {
            respond(exchange, 413, TEXT, "request body too large (max " + MAX_BODY_BYTES + " bytes)\n");
        } catch (ApiException e) {
            respond(exchange, e.status, TEXT, e.getMessage() + "\n");
        } catch (Exception e) {
            LOG.log(Level.WARNING, "request failed", e);
            respond(exchange, 500, TEXT, "internal error: " + e + "\n");
        }
        // the provider closes the exchange
    }

    private void handleHelp(HttpExchange exchange) throws IOException {
        if (!isRead(exchange)) {
            methodNotAllowed(exchange, "GET, HEAD, OPTIONS");
            return;
        }
        respond(exchange, 200, TEXT, Help.text());
    }

    private void handleState(HttpExchange exchange) throws IOException, ApiException {
        if (!isRead(exchange)) {
            methodNotAllowed(exchange, "GET, HEAD, OPTIONS");
            return;
        }
        respondJson(exchange, 200, onClient(engine::snapshot));
    }

    private void handleSet(HttpExchange exchange, Boolean enabled) throws IOException, ApiException {
        if (!isPost(exchange)) {
            methodNotAllowed(exchange, "POST, OPTIONS");
            return;
        }

        AuraSnapshot snapshot = onClient(() -> {
            if (enabled == null) {
                engine.toggle();
            } else {
                engine.setEnabled(enabled);
            }
            return engine.snapshot();
        });
        respondJson(exchange, 200, snapshot);
    }

    private void handleReload(HttpExchange exchange) throws IOException, ApiException {
        if (!isPost(exchange)) {
            methodNotAllowed(exchange, "POST, OPTIONS");
            return;
        }

        AuraSnapshot snapshot = onClient(() -> {
            engine.reloadSettings();
            return engine.snapshot();
        });
        respondJson(exchange, 200, snapshot);
    }

    private void handleConfig(HttpExchange exchange) throws IOException, ApiException {
        if (!isPost(exchange)) {
            methodNotAllowed(exchange, "POST, OPTIONS");
            return;
        }

        String body = new String(readBody(exchange), StandardCharsets.UTF_8).strip();
        if (body.isEmpty()) {
            respond(exchange, 400, TEXT,
                    "empty body - send a JSON object, e.g. {\"range\":4.5}\n");
            return;
        }

        JsonObject patch;
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (!parsed.isJsonObject()) {
                respond(exchange, 400, TEXT, "request body must be a JSON object\n");
                return;
            }
            patch = parsed.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            respond(exchange, 400, TEXT, "invalid JSON: " + e.getMessage() + "\n");
            return;
        }

        AuraSnapshot snapshot = onClient(() -> {
            List<String> errors = engine.applyConfigPatch(patch);
            if (!errors.isEmpty()) {
                throw new ApiException(400, String.join("\n", errors));
            }
            return engine.snapshot();
        });
        respondJson(exchange, 200, snapshot);
    }

    /**
     * Runs {@code task} on the Minecraft client thread and waits up to five seconds.
     *
     * <p>Mutations must land on the client thread because the tick loop reads the same settings;
     * keeping everything on one thread means no locks are needed.
     */
    private static <T> T onClient(Callable<T> task) throws ApiException {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            throw new ApiException(409, "Minecraft client is not running");
        }

        if (minecraft.isSameThread()) {
            return invoke(task);
        }

        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            minecraft.execute(() -> {
                try {
                    result.complete(task.call());
                } catch (Throwable t) {
                    result.completeExceptionally(t);
                }
            });
        } catch (RuntimeException e) {
            throw new ApiException(409, "Minecraft client is not accepting work (shutting down?): " + e);
        }

        try {
            return result.get(CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new ApiException(504, "the client thread did not answer within " + CLIENT_TIMEOUT_MS + " ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(500, "interrupted while waiting for the client thread");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ApiException api) {
                throw api;
            }
            throw new ApiException(500, cause == null ? String.valueOf(e) : String.valueOf(cause));
        }
    }

    private static <T> T invoke(Callable<T> task) throws ApiException {
        try {
            return task.call();
        } catch (ApiException e) {
            throw e;
        } catch (Throwable t) {
            throw new ApiException(500, "client task failed: " + t);
        }
    }

    private static boolean isRead(HttpExchange exchange) {
        return "GET".equals(exchange.getRequestMethod()) || "HEAD".equals(exchange.getRequestMethod());
    }

    private static boolean isPost(HttpExchange exchange) {
        return "POST".equals(exchange.getRequestMethod());
    }

    private static void methodNotAllowed(HttpExchange exchange, String allow) throws IOException {
        exchange.getResponseHeaders().set("Allow", allow);
        respond(exchange, 405, TEXT, "method not allowed: " + exchange.getRequestMethod()
                + " (allow: " + allow + ")\n\n" + Help.text());
    }

    private static byte[] readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) > 0) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new BodyTooLargeException();
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private static void respondJson(HttpExchange exchange, int status, AuraSnapshot snapshot) throws IOException {
        byte[] bytes = (snapshot.toJsonString() + "\n").getBytes(StandardCharsets.UTF_8);
        respond(exchange, status, JSON, bytes);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        respond(exchange, status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if ("HEAD".equals(exchange.getRequestMethod()) || body.length == 0) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static final class BodyTooLargeException extends IOException {
        private static final long serialVersionUID = 1L;
    }

    /** Carries the HTTP status the endpoint should answer with. */
    static final class ApiException extends Exception {
        private static final long serialVersionUID = 1L;
        final int status;

        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
