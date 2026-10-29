package dev.dsh.killaura;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TargetRulesTest {
    private static AuraSettings settings() {
        AuraSettings settings = new AuraSettings();
        settings.attackHostile = false;
        settings.attackFriendlyDrops = false;
        settings.attackFriendlyNoDrops = false;
        return settings;
    }

    private static TargetRules.Decision evaluate(String id, boolean player, boolean armorStand,
                                                 boolean invisible, boolean lineOfSight,
                                                 TargetClassifier.Kind kind, AuraSettings settings) {
        return TargetRules.evaluate(id, player, armorStand, invisible, lineOfSight, kind, settings);
    }

    @Test
    void denyBeatsAlways() {
        AuraSettings settings = settings();
        settings.denyTargets = List.of("minecraft:zombie");
        settings.alwaysTargets = List.of("minecraft:zombie");

        assertEquals(TargetRules.Decision.DENY,
                evaluate("minecraft:zombie", false, false, false, true,
                        TargetClassifier.Kind.HOSTILE, settings));
    }

    @Test
    void alwaysOverridesPlayerArmorStandInvisibleAndLineOfSight() {
        AuraSettings player = settings();
        player.targetPlayers = false;
        player.alwaysTargets = List.of("minecraft:player");
        assertEquals(TargetRules.Decision.FORCE,
                evaluate("minecraft:player", true, false, false, true,
                        TargetClassifier.Kind.FRIENDLY_DROPS, player));

        AuraSettings armorStand = settings();
        armorStand.targetArmorStands = false;
        armorStand.alwaysTargets = List.of("minecraft:armor_stand");
        assertEquals(TargetRules.Decision.FORCE,
                evaluate("minecraft:armor_stand", false, true, false, true,
                        TargetClassifier.Kind.FRIENDLY_NO_DROPS, armorStand));

        AuraSettings invisible = settings();
        invisible.ignoreInvisible = true;
        invisible.alwaysTargets = List.of("minecraft:zombie");
        assertEquals(TargetRules.Decision.FORCE,
                evaluate("minecraft:zombie", false, false, true, true,
                        TargetClassifier.Kind.HOSTILE, invisible));

        AuraSettings wall = settings();
        wall.throughWalls = false;
        wall.alwaysTargets = List.of("minecraft:zombie");
        assertEquals(TargetRules.Decision.FORCE,
                evaluate("minecraft:zombie", false, false, false, false,
                        TargetClassifier.Kind.HOSTILE, wall));
    }

    @Test
    void normalPlayerAndArmorStandTogglesApply() {
        AuraSettings settings = settings();

        settings.targetPlayers = true;
        assertEquals(TargetRules.Decision.NORMAL,
                evaluate("minecraft:player", true, false, false, true,
                        TargetClassifier.Kind.FRIENDLY_DROPS, settings));
        settings.targetPlayers = false;
        assertEquals(TargetRules.Decision.DENY,
                evaluate("minecraft:player", true, false, false, true,
                        TargetClassifier.Kind.FRIENDLY_DROPS, settings));

        settings.targetArmorStands = true;
        assertEquals(TargetRules.Decision.NORMAL,
                evaluate("minecraft:armor_stand", false, true, false, true,
                        TargetClassifier.Kind.FRIENDLY_NO_DROPS, settings));
        settings.targetArmorStands = false;
        assertEquals(TargetRules.Decision.DENY,
                evaluate("minecraft:armor_stand", false, true, false, true,
                        TargetClassifier.Kind.FRIENDLY_NO_DROPS, settings));

        settings.targetArmorStands = true;
        settings.ignoreInvisible = true;
        assertEquals(TargetRules.Decision.DENY,
                evaluate("minecraft:armor_stand", false, true, true, true,
                        TargetClassifier.Kind.FRIENDLY_NO_DROPS, settings));
    }

    @Test
    void invisibilityAndLineOfSightApplyToNormalTargets() {
        AuraSettings settings = settings();
        settings.attackHostile = true;

        settings.ignoreInvisible = true;
        assertEquals(TargetRules.Decision.DENY,
                evaluate("minecraft:zombie", false, false, true, true,
                        TargetClassifier.Kind.HOSTILE, settings));

        settings.ignoreInvisible = false;
        assertEquals(TargetRules.Decision.DENY,
                evaluate("minecraft:zombie", false, false, false, false,
                        TargetClassifier.Kind.HOSTILE, settings));

        settings.throughWalls = true;
        assertEquals(TargetRules.Decision.NORMAL,
                evaluate("minecraft:zombie", false, false, false, false,
                        TargetClassifier.Kind.HOSTILE, settings));
    }

    @Test
    void classificationSwitchesApplyLast() {
        AuraSettings settings = settings();

        settings.attackHostile = true;
        assertEquals(TargetRules.Decision.NORMAL,
                evaluate("minecraft:zombie", false, false, false, true,
                        TargetClassifier.Kind.HOSTILE, settings));

        settings.attackFriendlyDrops = true;
        assertEquals(TargetRules.Decision.NORMAL,
                evaluate("minecraft:cow", false, false, false, true,
                        TargetClassifier.Kind.FRIENDLY_DROPS, settings));
        assertEquals(TargetRules.Decision.DENY,
                evaluate("minecraft:villager", false, false, false, true,
                        TargetClassifier.Kind.FRIENDLY_NO_DROPS, settings));

        settings.attackHostile = false;
        settings.attackFriendlyDrops = false;
        settings.attackFriendlyNoDrops = true;
        assertEquals(TargetRules.Decision.NORMAL,
                evaluate("minecraft:villager", false, false, false, true,
                        TargetClassifier.Kind.FRIENDLY_NO_DROPS, settings));
        assertEquals(TargetRules.Decision.DENY,
                evaluate("minecraft:cow", false, false, false, true,
                        TargetClassifier.Kind.FRIENDLY_DROPS, settings));
        assertEquals(TargetRules.Decision.DENY,
                evaluate("minecraft:wolf", false, false, false, true,
                        TargetClassifier.Kind.NEUTRAL, settings));
    }

    @Test
    void neutralIsOnlyAttackableThroughAlwaysList() {
        AuraSettings settings = settings();
        settings.attackHostile = true;
        settings.attackFriendlyDrops = true;
        settings.attackFriendlyNoDrops = true;

        assertEquals(TargetRules.Decision.DENY,
                evaluate("minecraft:wolf", false, false, false, true,
                        TargetClassifier.Kind.NEUTRAL, settings));

        settings.alwaysTargets = List.of("minecraft:wolf");
        assertEquals(TargetRules.Decision.FORCE,
                evaluate("minecraft:wolf", false, false, false, false,
                        TargetClassifier.Kind.NEUTRAL, settings));
    }
}
