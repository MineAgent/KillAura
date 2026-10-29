package dev.dsh.killaura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        assertTrue(settings.attackHostile);
        assertFalse(settings.attackFriendlyDrops);
        assertFalse(settings.attackFriendlyNoDrops);
        assertTrue(settings.denyTargets.isEmpty());
        assertTrue(settings.alwaysTargets.isEmpty());
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
        assertTrue(settings.attackHostile);
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
        assertTrue(settings.attackHostile);
        assertFalse(settings.attackFriendlyDrops);
        assertFalse(settings.attackFriendlyNoDrops);
    }

    @Test
    void writesFileBackAfterNormalizing() throws Exception {
        Path file = dir.resolve("killaura.json");
        Files.writeString(file, "{\"range\": 1.0, \"priority\": \"angle\"}", StandardCharsets.UTF_8);

        AuraSettings.fromFile(file);
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.contains("\"range\": 1.0"));
        assertTrue(written.contains("\"_help\""));
        assertTrue(written.contains("\"attackHostile\": true"));
        assertTrue(written.contains("\"denyTargets\": []"));
    }

    @Test
    void migratesLegacyTargetFields() throws Exception {
        Path file = dir.resolve("killaura.json");
        Files.writeString(file,
                "{\"targetHostile\": false, \"targetPassive\": true, \"showToggleMessage\": false}",
                StandardCharsets.UTF_8);

        AuraSettings settings = AuraSettings.fromFile(file);
        assertFalse(settings.attackHostile);
        assertTrue(settings.attackFriendlyDrops);
        assertTrue(settings.attackFriendlyNoDrops);

        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(written.contains("targetHostile"));
        assertFalse(written.contains("targetPassive"));
        assertFalse(written.contains("showToggleMessage"));
        assertTrue(written.contains("\"attackHostile\": false"));
    }

    @Test
    void newFieldsWinOverLegacyFields() throws Exception {
        Path file = dir.resolve("killaura.json");
        Files.writeString(file, """
                {
                  "targetHostile": false,
                  "attackHostile": true,
                  "targetPassive": true,
                  "attackFriendlyDrops": false,
                  "attackFriendlyNoDrops": true
                }
                """, StandardCharsets.UTF_8);

        AuraSettings settings = AuraSettings.fromFile(file);
        assertTrue(settings.attackHostile);
        assertFalse(settings.attackFriendlyDrops);
        assertTrue(settings.attackFriendlyNoDrops);
    }

    @Test
    void normalizesAndDeduplicatesTargetLists() throws Exception {
        Path file = dir.resolve("killaura.json");
        Files.writeString(file, """
                {
                  "denyTargets": ["ZOMBIE", "minecraft:zombie", "minecraft:player", "Bad ID"],
                  "alwaysTargets": ["creeper", "minecraft:creeper"]
                }
                """, StandardCharsets.UTF_8);

        AuraSettings settings = AuraSettings.fromFile(file);
        assertEquals(List.of("minecraft:player", "minecraft:zombie"), settings.denyTargets);
        assertEquals(List.of("minecraft:creeper"), settings.alwaysTargets);
    }

    @Test
    void nullListsBecomeEmpty() throws Exception {
        Path file = dir.resolve("killaura.json");
        Files.writeString(file, "{\"denyTargets\": null, \"alwaysTargets\": null}", StandardCharsets.UTF_8);

        AuraSettings settings = AuraSettings.fromFile(file);
        assertTrue(settings.denyTargets.isEmpty());
        assertTrue(settings.alwaysTargets.isEmpty());
    }

    @Test
    void refreshesHelpTextFromExistingFile() throws Exception {
        Path file = dir.resolve("killaura.json");
        Files.writeString(file, "{\"_help\": \"old help text\"}", StandardCharsets.UTF_8);

        AuraSettings settings = AuraSettings.fromFile(file);
        assertTrue(settings._help.contains("attackFriendlyDrops"));

        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(written.contains("old help text"));
    }

    @Test
    void wrongFieldTypesFallBackToDefaults() throws Exception {
        Path file = dir.resolve("killaura.json");
        Files.writeString(file, "{\"range\": \"far\", \"denyTargets\": {}}", StandardCharsets.UTF_8);

        AuraSettings settings = AuraSettings.fromFile(file);
        assertEquals(3.0, settings.range);
        assertTrue(settings.attackHostile);
        assertTrue(settings.denyTargets.isEmpty());
    }

    @Test
    void emptyFileUsesDefaults() throws Exception {
        Path file = dir.resolve("killaura.json");
        Files.writeString(file, "", StandardCharsets.UTF_8);

        AuraSettings settings = AuraSettings.fromFile(file);
        assertEquals(3.0, settings.range);
        assertTrue(settings.attackHostile);
        assertFalse(Files.readString(file, StandardCharsets.UTF_8).isEmpty());
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
