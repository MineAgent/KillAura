// SPDX-License-Identifier: LGPL-3.0-only
// Copyright (C) 2026 MineAgent

package dev.dsh.killaura;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistent settings, serialized to {@code config/killaura.json}.
 *
 * <p>The field names are kept identical to the original mod's file so an existing configuration
 * can be dropped in unchanged. Every load is sanitized and written back, which also creates the
 * file with defaults on first launch.
 */
public final class AuraSettings {
    private static final Logger LOG = LoggerFactory.getLogger("Kill Aura");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final double DEFAULT_RANGE = 3.0;
    private static final double MIN_RANGE = 0.5;
    private static final double MAX_RANGE = 6.0;
    private static final double DEFAULT_FOV = 360.0;
    private static final double MIN_FOV = 1.0;
    private static final double MAX_FOV = 360.0;
    private static final double DEFAULT_CHARGE = 0.0;

    public String _help = "range: attack reach in blocks (0.5-6.0, vanilla is 3.0) | rotation: silent, visible, none"
            + " | priority: closest, lowestHealth, angle | fov: 360 = no angle limit"
            + " | minAttackCharge: 0.0 = attack whenever possible, 1.0 = only full-charge hits";

    public double range = DEFAULT_RANGE;
    public boolean targetPlayers = true;
    public boolean targetHostile = true;
    public boolean targetPassive = false;
    public boolean targetArmorStands = false;
    public boolean ignoreInvisible = false;
    public boolean throughWalls = false;
    public String rotation = "visible";
    public String priority = "closest";
    public double fov = DEFAULT_FOV;
    public double minAttackCharge = DEFAULT_CHARGE;
    public boolean pauseInScreens = true;
    public boolean pauseWhileUsingItem = true;

    public static AuraSettings fromFile(Path file) {
        AuraSettings loaded = null;
        try {
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    loaded = GSON.fromJson(reader, AuraSettings.class);
                }
            }
        } catch (Exception e) {
            LOG.warn("Could not read {}, using defaults: {}", file, e.toString());
            return new AuraSettings();
        }

        AuraSettings result = loaded != null ? loaded : new AuraSettings();
        result.normalize();
        result.toFile(file);
        return result;
    }

    /** Independent copy, used by the HTTP config endpoint so a rejected patch cannot half-apply. */
    AuraSettings copy() {
        return GSON.fromJson(GSON.toJson(this), AuraSettings.class);
    }

    /**
     * Applies a partial JSON patch in place.
     *
     * <p>Unknown fields, wrong types and out-of-range values are collected as errors. The caller
     * applies the patch to a {@link #copy() copy} and only swaps it in when the returned list is
     * empty, so a failed request never modifies the live settings or the file.
     */
    List<String> applyPatch(JsonObject patch) {
        List<String> errors = new ArrayList<>();

        for (Map.Entry<String, JsonElement> entry : patch.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            switch (key) {
                case "range" -> range = number(errors, key, value, range, MIN_RANGE, MAX_RANGE);
                case "fov" -> fov = number(errors, key, value, fov, MIN_FOV, MAX_FOV);
                case "minAttackCharge" -> minAttackCharge = number(errors, key, value, minAttackCharge, 0.0, 1.0);
                case "targetPlayers" -> targetPlayers = bool(errors, key, value, targetPlayers);
                case "targetHostile" -> targetHostile = bool(errors, key, value, targetHostile);
                case "targetPassive" -> targetPassive = bool(errors, key, value, targetPassive);
                case "targetArmorStands" -> targetArmorStands = bool(errors, key, value, targetArmorStands);
                case "ignoreInvisible" -> ignoreInvisible = bool(errors, key, value, ignoreInvisible);
                case "throughWalls" -> throughWalls = bool(errors, key, value, throughWalls);
                case "pauseInScreens" -> pauseInScreens = bool(errors, key, value, pauseInScreens);
                case "pauseWhileUsingItem" -> pauseWhileUsingItem = bool(errors, key, value, pauseWhileUsingItem);
                case "rotation" -> rotation = rotation(errors, value, rotation);
                case "priority" -> priority = priority(errors, value, priority);
                case "_help" -> errors.add("_help is read-only");
                default -> errors.add("unknown field: " + key);
            }
        }

        if (errors.isEmpty()) {
            normalize();
        }
        return errors;
    }

    public void toFile(Path file) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            LOG.warn("Could not write {}: {}", file, e.toString());
        }
    }

    private void normalize() {
        range = clampFinite(range, DEFAULT_RANGE, MIN_RANGE, MAX_RANGE);
        fov = clampFinite(fov, DEFAULT_FOV, MIN_FOV, MAX_FOV);
        minAttackCharge = clampFinite(minAttackCharge, DEFAULT_CHARGE, 0.0, 1.0);

        rotation = canonicalRotation(rotation);
        priority = canonicalPriority(priority);
    }

    private static double number(List<String> errors, String key, JsonElement value,
                                 double current, double min, double max) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            errors.add(key + " must be a number");
            return current;
        }

        double parsed = value.getAsDouble();
        if (!Double.isFinite(parsed) || parsed < min || parsed > max) {
            errors.add(key + " must be between " + min + " and " + max);
            return current;
        }
        return parsed;
    }

    private static boolean bool(List<String> errors, String key, JsonElement value, boolean current) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            errors.add(key + " must be true or false");
            return current;
        }
        return value.getAsBoolean();
    }

    private static String string(List<String> errors, String key, JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            errors.add(key + " must be a string");
            return null;
        }
        return value.getAsString();
    }

    private static String rotation(List<String> errors, JsonElement value, String current) {
        String raw = string(errors, "rotation", value);
        if (raw == null) {
            return current;
        }

        String canonical = raw.trim().toLowerCase(Locale.ROOT);
        if (canonical.equals("silent") || canonical.equals("visible") || canonical.equals("none")) {
            return canonical;
        }

        errors.add("rotation must be one of: silent, visible, none");
        return current;
    }

    private static String priority(List<String> errors, JsonElement value, String current) {
        String raw = string(errors, "priority", value);
        if (raw == null) {
            return current;
        }

        String canonical = raw.trim().toLowerCase(Locale.ROOT);
        return switch (canonical) {
            case "closest" -> "closest";
            case "lowesthealth", "health" -> "lowestHealth";
            case "angle" -> "angle";
            default -> {
                errors.add("priority must be one of: closest, lowestHealth, health, angle");
                yield current;
            }
        };
    }

    private static double clampFinite(double value, double fallback, double min, double max) {
        double v = Double.isNaN(value) ? fallback : value;
        return Math.max(min, Math.min(max, v));
    }

    private static String canonicalRotation(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "visible", "none" -> value;
            default -> "silent";
        };
    }

    private static String canonicalPriority(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "lowesthealth", "health" -> "lowestHealth";
            case "angle" -> "angle";
            default -> "closest";
        };
    }
}
