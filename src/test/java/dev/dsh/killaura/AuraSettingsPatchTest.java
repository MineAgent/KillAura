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
        assertEquals(3.0, settings.range);
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
    void rejectsWrongTypes() {
        AuraSettings candidate = new AuraSettings().copy();

        List<String> errors = candidate.applyPatch(json("{\"range\":\"far\",\"targetPlayers\":1,\"rotation\":5}"));

        assertEquals(3, errors.size(), errors.toString());
        assertEquals(3.0, candidate.range);
        assertTrue(candidate.targetPlayers);
        assertEquals("visible", candidate.rotation);
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
        candidate.applyPatch(json("{\"range\":5.0}"));

        assertEquals(3.0, settings.range);
        assertEquals(5.0, candidate.range);
        assertFalse(settings == candidate);
    }
}
