// SPDX-License-Identifier: LGPL-3.0-only
// Copyright (C) 2026 MineAgent

package dev.dsh.killaura;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/**
 * Immutable view of the aura for {@code GET /ka/state} and the action endpoints.
 *
 * <p>Contains no Minecraft types, so JSON shaping can be tested without launching the game.
 */
public record AuraSnapshot(
        boolean enabled,
        boolean inWorld,
        String player,
        JsonObject config,
        Target target,
        LastAttack lastAttack) {

    /**
     * Gson omits JSON-null members unless serialization of nulls is enabled; the API always
     * returns {@code player}, {@code target} and {@code lastAttack}, so enable it here.
     */
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    /** The entity the aura would attack right now, or {@code null} when there is none. */
    public record Target(String name, int id, double distance, float health) {
    }

    /** The last entity actually attacked, with the age of that hit in milliseconds. */
    public record LastAttack(String name, long agoMs) {
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("ok", true);
        root.addProperty("enabled", enabled);
        root.addProperty("inWorld", inWorld);
        root.addProperty("player", player);
        root.add("config", config);

        if (target == null) {
            root.add("target", JsonNull.INSTANCE);
        } else {
            JsonObject json = new JsonObject();
            json.addProperty("name", target.name());
            json.addProperty("id", target.id());
            json.addProperty("distance", target.distance());
            json.addProperty("health", target.health());
            root.add("target", json);
        }

        if (lastAttack == null) {
            root.add("lastAttack", JsonNull.INSTANCE);
        } else {
            JsonObject json = new JsonObject();
            json.addProperty("name", lastAttack.name());
            json.addProperty("agoMs", lastAttack.agoMs());
            root.add("lastAttack", json);
        }

        return root;
    }

    /** Serialized form used by the endpoint; keeps the documented null-valued keys present. */
    public String toJsonString() {
        return GSON.toJson(toJson());
    }
}
