package dev.dsh.killaura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

class AuraSnapshotTest {
    @Test
    void serializesEmptyState() {
        JsonObject config = new JsonObject();
        config.addProperty("range", 3.0);

        JsonObject json = new AuraSnapshot(false, false, null, config, null, null).toJson();

        assertEquals(true, json.get("ok").getAsBoolean());
        assertFalse(json.get("enabled").getAsBoolean());
        assertFalse(json.get("inWorld").getAsBoolean());
        assertTrue(json.get("player").isJsonNull());
        assertTrue(json.get("target").isJsonNull());
        assertTrue(json.get("lastAttack").isJsonNull());
        assertEquals(3.0, json.getAsJsonObject("config").get("range").getAsDouble());

        String serialized = new AuraSnapshot(false, false, null, config, null, null).toJsonString();
        assertTrue(serialized.contains("\"player\":null"));
        assertTrue(serialized.contains("\"target\":null"));
        assertTrue(serialized.contains("\"lastAttack\":null"));
    }

    @Test
    void serializesTargetAndLastAttack() {
        AuraSnapshot snapshot = new AuraSnapshot(
                true,
                true,
                "Steve",
                new JsonObject(),
                new AuraSnapshot.Target("Zombie", 42, 2.31, 20.0F),
                new AuraSnapshot.LastAttack("Zombie", 120L));

        JsonObject json = snapshot.toJson();

        assertEquals("Steve", json.get("player").getAsString());
        assertEquals("Zombie", json.getAsJsonObject("target").get("name").getAsString());
        assertEquals(42, json.getAsJsonObject("target").get("id").getAsInt());
        assertEquals(2.31, json.getAsJsonObject("target").get("distance").getAsDouble());
        assertEquals(120L, json.getAsJsonObject("lastAttack").get("agoMs").getAsLong());
    }
}
