package dev.dsh.killaura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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

    private static TargetRules.Tier tier(String id, TargetClassifier.Kind kind,
                                        boolean player, boolean armorStand, AuraSettings settings) {
        return TargetRules.tierFor(id, kind, player, armorStand, settings);
    }

    @Test
    void denyBeatsAlways() {
        AuraSettings settings = settings();
        settings.denyTargets = List.of("minecraft:zombie");
        settings.alwaysTargetsHigh = List.of("minecraft:zombie");

        assertEquals(TargetRules.Decision.DENY,
                evaluate("minecraft:zombie", false, false, false, true,
                        TargetClassifier.Kind.HOSTILE, settings));
    }

    @Test
    void everyAlwaysTierForcesEligibility() {
        for (int i = 0; i < 4; i++) {
            AuraSettings settings = settings();
            settings.ignoreInvisible = true;
            settings.throughWalls = false;
            settings.targetPlayers = false;
            settings.targetArmorStands = false;
            if (i == 0) {
                settings.alwaysTargetsHigh = List.of("minecraft:zombie");
            } else if (i == 1) {
                settings.alwaysTargetsMedium = List.of("minecraft:zombie");
            } else if (i == 2) {
                settings.alwaysTargetsLow = List.of("minecraft:zombie");
            } else {
                settings.alwaysTargetsLowest = List.of("minecraft:zombie");
            }

            assertEquals(TargetRules.Decision.FORCE,
                    evaluate("minecraft:zombie", false, false, true, false,
                            TargetClassifier.Kind.NEUTRAL, settings));
        }
    }

    @Test
    void alwaysOverridesPlayerAndArmorStandToggles() {
        AuraSettings player = settings();
        player.targetPlayers = false;
        player.alwaysTargetsHigh = List.of("minecraft:player");
        assertEquals(TargetRules.Decision.FORCE,
                evaluate("minecraft:player", true, false, false, true,
                        TargetClassifier.Kind.FRIENDLY_DROPS, player));

        AuraSettings armorStand = settings();
        armorStand.targetArmorStands = false;
        armorStand.alwaysTargetsMedium = List.of("minecraft:armor_stand");
        assertEquals(TargetRules.Decision.FORCE,
                evaluate("minecraft:armor_stand", false, true, false, true,
                        TargetClassifier.Kind.FRIENDLY_NO_DROPS, armorStand));
    }

    @Test
    void normalPlayerAndArmorStandTogglesApply() {
        AuraSettings settings = settings();

        settings.targetPlayers = true;
        assertEquals(TargetRules.Decision.NORMAL,
                evaluate("minecraft:player", true, false, false, true,
                        TargetClassifier.Kind.FRIENDLY_DROPS, settings));
        assertEquals(TargetRules.Tier.FRIENDLY_NO_DROPS,
                tier("minecraft:player", TargetClassifier.Kind.FRIENDLY_DROPS, true, false, settings));
        settings.targetPlayers = false;
        assertEquals(TargetRules.Decision.DENY,
                evaluate("minecraft:player", true, false, false, true,
                        TargetClassifier.Kind.FRIENDLY_DROPS, settings));
        assertNull(tier("minecraft:player", TargetClassifier.Kind.FRIENDLY_DROPS, true, false, settings));

        settings.targetArmorStands = true;
        assertEquals(TargetRules.Decision.NORMAL,
                evaluate("minecraft:armor_stand", false, true, false, true,
                        TargetClassifier.Kind.FRIENDLY_NO_DROPS, settings));
        assertEquals(TargetRules.Tier.FRIENDLY_NO_DROPS,
                tier("minecraft:armor_stand", TargetClassifier.Kind.FRIENDLY_NO_DROPS,
                        false, true, settings));
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
        assertEquals(TargetRules.Tier.HOSTILE,
                tier("minecraft:zombie", TargetClassifier.Kind.HOSTILE, false, false, settings));

        settings.attackFriendlyDrops = true;
        assertEquals(TargetRules.Decision.NORMAL,
                evaluate("minecraft:cow", false, false, false, true,
                        TargetClassifier.Kind.FRIENDLY_DROPS, settings));
        assertEquals(TargetRules.Tier.FRIENDLY_DROPS,
                tier("minecraft:cow", TargetClassifier.Kind.FRIENDLY_DROPS, false, false, settings));
        assertEquals(TargetRules.Decision.DENY,
                evaluate("minecraft:villager", false, false, false, true,
                        TargetClassifier.Kind.FRIENDLY_NO_DROPS, settings));
        assertNull(tier("minecraft:villager", TargetClassifier.Kind.FRIENDLY_NO_DROPS,
                false, false, settings));

        settings.attackHostile = false;
        settings.attackFriendlyDrops = false;
        settings.attackFriendlyNoDrops = true;
        assertEquals(TargetRules.Decision.NORMAL,
                evaluate("minecraft:villager", false, false, false, true,
                        TargetClassifier.Kind.FRIENDLY_NO_DROPS, settings));
        assertEquals(TargetRules.Tier.FRIENDLY_NO_DROPS,
                tier("minecraft:villager", TargetClassifier.Kind.FRIENDLY_NO_DROPS,
                        false, false, settings));
        assertEquals(TargetRules.Decision.DENY,
                evaluate("minecraft:cow", false, false, false, true,
                        TargetClassifier.Kind.FRIENDLY_DROPS, settings));
        assertNull(tier("minecraft:cow", TargetClassifier.Kind.FRIENDLY_DROPS,
                false, false, settings));
        assertEquals(TargetRules.Decision.DENY,
                evaluate("minecraft:wolf", false, false, false, true,
                        TargetClassifier.Kind.NEUTRAL, settings));
        assertNull(tier("minecraft:wolf", TargetClassifier.Kind.NEUTRAL, false, false, settings));
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

        settings.alwaysTargetsLowest = List.of("minecraft:wolf");
        assertEquals(TargetRules.Decision.FORCE,
                evaluate("minecraft:wolf", false, false, false, false,
                        TargetClassifier.Kind.NEUTRAL, settings));
        assertEquals(TargetRules.Tier.ALWAYS_LOWEST,
                tier("minecraft:wolf", TargetClassifier.Kind.NEUTRAL, false, false, settings));
    }

    @Test
    void alwaysTierUsesHighestMatchingList() {
        AuraSettings settings = settings();
        settings.alwaysTargetsHigh = List.of("minecraft:zombie");
        settings.alwaysTargetsMedium = List.of("minecraft:zombie");
        settings.alwaysTargetsLow = List.of("minecraft:zombie");
        settings.alwaysTargetsLowest = List.of("minecraft:zombie");

        assertEquals(TargetRules.Tier.ALWAYS_HIGH,
                tier("minecraft:zombie", TargetClassifier.Kind.HOSTILE, false, false, settings));

        settings.alwaysTargetsHigh = List.of();
        assertEquals(TargetRules.Tier.ALWAYS_MEDIUM,
                tier("minecraft:zombie", TargetClassifier.Kind.HOSTILE, false, false, settings));

        settings.alwaysTargetsLowest = List.of("minecraft:wolf");
        assertEquals(TargetRules.Tier.ALWAYS_LOWEST,
                tier("minecraft:wolf", TargetClassifier.Kind.NEUTRAL, false, false, settings));
    }

    @Test
    void alwaysTiersOverrideNormalTierAndSwitch() {
        AuraSettings settings = settings();
        settings.attackHostile = true;
        settings.alwaysTargetsLow = List.of("minecraft:cow");

        assertEquals(TargetRules.Tier.ALWAYS_LOW,
                tier("minecraft:cow", TargetClassifier.Kind.FRIENDLY_DROPS, false, false, settings));
        assertEquals(TargetRules.Tier.HOSTILE,
                tier("minecraft:zombie", TargetClassifier.Kind.HOSTILE, false, false, settings));
    }
}
