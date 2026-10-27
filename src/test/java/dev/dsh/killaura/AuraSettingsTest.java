package dev.dsh.killaura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuraSettingsTest {
    @TempDir
    Path dir;

    @Test
    void createsADefaultFileOnFirstLaunch() throws Exception {
        Path file = dir.resolve("config/killaura.json");
        AuraSettings settings = AuraSettings.fromFile(file);

        assertTrue(Files.exists(file));
        assertEquals(3.0, settings.range);
        assertEquals(360.0, settings.fov);
        assertEquals("visible", settings.rotation);
        assertEquals("closest", settings.priority);
        assertEquals(0.0, settings.minAttackCharge);
        assertTrue(settings.targetPlayers);
        assertFalse(settings.targetPassive);
        assertFalse(settings.targetArmorStands);
    }

    @Test
    void clampsAndCanonicalizesInvalidValues() throws Exception {
        Path file = dir.resolve("killaura.json");
        Files.writeString(file, """
                {
                  "range": 99.0,
                  "fov": 0.0,
                  "minAttackCharge": 4.0,
                  "rotation": " VISIBLE ",
                  "priority": "Health",
                  "targetPlayers": false
                }
                """, StandardCharsets.UTF_8);

        AuraSettings settings = AuraSettings.fromFile(file);
        assertEquals(6.0, settings.range);
        assertEquals(1.0, settings.fov);
        assertEquals(1.0, settings.minAttackCharge);
        assertEquals("visible", settings.rotation);
        assertEquals("lowestHealth", settings.priority);
        assertFalse(settings.targetPlayers);
        // Fields missing from the file keep their defaults.
        assertFalse(settings.throughWalls);
    }

    @Test
    void emptyJsonKeepsEveryDefault() throws Exception {
        Path file = dir.resolve("killaura.json");
        Files.writeString(file, "{}", StandardCharsets.UTF_8);

        AuraSettings settings = AuraSettings.fromFile(file);
        assertEquals(3.0, settings.range);
        assertEquals("visible", settings.rotation);
        assertEquals("closest", settings.priority);
        assertTrue(settings.pauseInScreens);
    }

    @Test
    void writesFileBackAfterNormalizing() throws Exception {
        Path file = dir.resolve("killaura.json");
        Files.writeString(file, "{\"range\": 1.0, \"priority\": \"angle\"}", StandardCharsets.UTF_8);

        AuraSettings.fromFile(file);
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.contains("\"range\": 1.0"));
        assertTrue(written.contains("\"_help\""));
    }

    @Test
    void dropsTheRemovedShowToggleMessageField() throws Exception {
        Path file = dir.resolve("killaura.json");
        Files.writeString(file,
                "{\"range\": 2.0, \"showToggleMessage\": false}",
                StandardCharsets.UTF_8);

        AuraSettings settings = AuraSettings.fromFile(file);
        assertEquals(2.0, settings.range);

        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(written.contains("showToggleMessage"));
    }
}
