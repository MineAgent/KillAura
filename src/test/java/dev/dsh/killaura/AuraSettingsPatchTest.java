package dev.dsh.killaura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuraSettingsPatchTest {
    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void partialPatchOnlyTouchesNamedFields() {
        AuraSettings settings = new AuraSettings();
        AuraSettings candidate = settings.copy();

        List<String> errors = candidate.applyPatch(json("{\"range\":4.5,\"rotation\":\"VISIBLE\"}"));

        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(4.5, candidate.range);
        assertEquals("visible", candidate.rotation);
        // Untouched fields keep their values.
        assertEquals(360.0, candidate.fov);
        assertEquals("closest", candidate.priority);
        assertTrue(candidate.attackHostile);
        assertFalse(candidate.attackFriendlyDrops);
        assertEquals(3.0, settings.range);
    }

    @Test
    void patchesAttackSwitchesAndTargetLists() {
        AuraSettings candidate = new AuraSettings().copy();

        List<String> errors = candidate.applyPatch(json("""
                {
                  "attackHostile": false,
                  "attackFriendlyDrops": true,
                  "attackFriendlyNoDrops": true,
                  "denyTargets": ["ZOMBIE", "minecraft:zombie", "minecraft:player"],
                  "alwaysTargetsHigh": ["creeper"],
                  "alwaysTargetsMedium": ["COW"],
                  "alwaysTargetsLow": ["bat"],
                  "alwaysTargetsLowest": ["ZOMBIE"]
                }
                """));

        assertTrue(errors.isEmpty(), errors.toString());
        assertFalse(candidate.attackHostile);
        assertTrue(candidate.attackFriendlyDrops);
        assertTrue(candidate.attackFriendlyNoDrops);
        assertEquals(List.of("minecraft:player", "minecraft:zombie"), candidate.denyTargets);
        assertEquals(List.of("minecraft:creeper"), candidate.alwaysTargetsHigh);
        assertEquals(List.of("minecraft:cow"), candidate.alwaysTargetsMedium);
        assertEquals(List.of("minecraft:bat"), candidate.alwaysTargetsLow);
        assertEquals(List.of("minecraft:zombie"), candidate.alwaysTargetsLowest);
    }

    @Test
    void emptyArraysClearLists() {
        AuraSettings candidate = new AuraSettings().copy();
        candidate.denyTargets = List.of("minecraft:zombie");
        candidate.alwaysTargetsHigh = List.of("minecraft:cow");
        candidate.alwaysTargetsMedium = List.of("minecraft:cow");
        candidate.alwaysTargetsLow = List.of("minecraft:cow");
        candidate.alwaysTargetsLowest = List.of("minecraft:cow");

        assertTrue(candidate.applyPatch(json("""
                {
                  "denyTargets": [],
                  "alwaysTargetsHigh": [],
                  "alwaysTargetsMedium": [],
                  "alwaysTargetsLow": [],
                  "alwaysTargetsLowest": []
                }
                """)).isEmpty());
        assertTrue(candidate.denyTargets.isEmpty());
        assertTrue(candidate.alwaysTargetsHigh.isEmpty());
        assertTrue(candidate.alwaysTargetsMedium.isEmpty());
        assertTrue(candidate.alwaysTargetsLow.isEmpty());
        assertTrue(candidate.alwaysTargetsLowest.isEmpty());
    }

    @Test
    void rejectsOutOfRangeNumbers() {
        AuraSettings settings = new AuraSettings();
        AuraSettings candidate = settings.copy();

        List<String> errors = candidate.applyPatch(json("{\"range\":99,\"fov\":0,\"minAttackCharge\":4}"));

        assertEquals(3, errors.size(), errors.toString());
        assertEquals(3.0, candidate.range);
        assertEquals(360.0, candidate.fov);
        assertEquals(0.0, candidate.minAttackCharge);
        assertEquals(3.0, settings.range, "the live settings must not change");
    }

    @Test
    void rejectsUnknownAndReadOnlyFields() {
        AuraSettings candidate = new AuraSettings().copy();

        List<String> errors = candidate.applyPatch(json("{\"nope\":1,\"_help\":\"x\"}"));

        assertEquals(2, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("nope"));
        assertTrue(errors.get(1).contains("_help"));
    }

    @Test
    void rejectsRemovedLegacyTargetFields() {
        AuraSettings candidate = new AuraSettings().copy();

        List<String> errors = candidate.applyPatch(json("{\"targetHostile\":false,\"targetPassive\":true}"));

        assertEquals(2, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("attackHostile"));
        assertTrue(errors.get(1).contains("attackFriendlyDrops"));
    }

    @Test
    void rejectsRemovedAlwaysTargetsField() {
        AuraSettings candidate = new AuraSettings().copy();

        List<String> errors = candidate.applyPatch(json("{\"alwaysTargets\":[\"minecraft:cow\"]}"));

        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("alwaysTargetsHigh"));
        assertTrue(candidate.alwaysTargetsHigh.isEmpty());
    }

    @Test
    void rejectsInvalidTargetArrays() {
        AuraSettings candidate = new AuraSettings().copy();

        List<String> errors = candidate.applyPatch(json("""
                {
                  "denyTargets": "minecraft:zombie",
                  "alwaysTargetsHigh": ["minecraft:cow", 5, "bad id"]
                }
                """));

        assertEquals(3, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("denyTargets"));
        assertTrue(errors.get(1).contains("alwaysTargetsHigh"));
        assertTrue(errors.get(2).contains("bad id"));
    }

    @Test
    void rejectsWrongTypes() {
        AuraSettings candidate = new AuraSettings().copy();

        List<String> errors = candidate.applyPatch(json(
                "{\"range\":\"far\",\"targetPlayers\":1,\"rotation\":5,\"attackHostile\":\"yes\"}"));

        assertEquals(4, errors.size(), errors.toString());
        assertEquals(3.0, candidate.range);
        assertTrue(candidate.targetPlayers);
        assertEquals("visible", candidate.rotation);
        assertTrue(candidate.attackHostile);
    }

    @Test
    void canonicalizesPriorityAliases() {
        AuraSettings candidate = new AuraSettings().copy();

        assertTrue(candidate.applyPatch(json("{\"priority\":\"Health\"}")).isEmpty());
        assertEquals("lowestHealth", candidate.priority);

        assertTrue(candidate.applyPatch(json("{\"priority\":\"ANGLE\"}")).isEmpty());
        assertEquals("angle", candidate.priority);
    }

    @Test
    void emptyPatchIsANoOp() {
        AuraSettings candidate = new AuraSettings().copy();

        assertTrue(candidate.applyPatch(json("{}")).isEmpty());
        assertEquals(3.0, candidate.range);
    }

    @Test
    void copyIsIndependent() {
        AuraSettings settings = new AuraSettings();
        AuraSettings candidate = settings.copy();
        candidate.applyPatch(json("""
                {"range":5.0,"denyTargets":["minecraft:zombie"]}
                """));

        assertEquals(3.0, settings.range);
        assertEquals(5.0, candidate.range);
        assertTrue(settings.denyTargets.isEmpty());
        assertEquals(List.of("minecraft:zombie"), candidate.denyTargets);
        assertFalse(settings == candidate);
    }
}
