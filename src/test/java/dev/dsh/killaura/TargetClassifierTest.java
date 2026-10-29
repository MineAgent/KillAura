package dev.dsh.killaura;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TargetClassifierTest {
    @Test
    void classifiesAlwaysHostileMobs() {
        assertEquals(TargetClassifier.Kind.HOSTILE,
                TargetClassifier.classify("minecraft:zombie", true, false, true));
        assertEquals(TargetClassifier.Kind.HOSTILE,
                TargetClassifier.classify("minecraft:creeper", true, false, true));
        assertEquals(TargetClassifier.Kind.HOSTILE,
                TargetClassifier.classify("minecraft:piglin_brute", true, false, true));
    }

    @Test
    void classifiesConditionalMobsAsNeutral() {
        assertEquals(TargetClassifier.Kind.NEUTRAL,
                TargetClassifier.classify("minecraft:enderman", true, true, true));
        assertEquals(TargetClassifier.Kind.NEUTRAL,
                TargetClassifier.classify("minecraft:zombified_piglin", true, true, true));
        assertEquals(TargetClassifier.Kind.NEUTRAL,
                TargetClassifier.classify("minecraft:wolf", false, true, true));
        assertEquals(TargetClassifier.Kind.NEUTRAL,
                TargetClassifier.classify("minecraft:iron_golem", false, true, true));
        assertEquals(TargetClassifier.Kind.NEUTRAL,
                TargetClassifier.classify("minecraft:piglin", true, false, true));
        assertEquals(TargetClassifier.Kind.NEUTRAL,
                TargetClassifier.classify("minecraft:llama", false, false, true));
        assertEquals(TargetClassifier.Kind.NEUTRAL,
                TargetClassifier.classify("minecraft:goat", false, false, true));
    }

    @Test
    void classifiesFriendlyMobsByDropTable() {
        assertEquals(TargetClassifier.Kind.FRIENDLY_DROPS,
                TargetClassifier.classify("minecraft:cow", false, false, true));
        assertEquals(TargetClassifier.Kind.FRIENDLY_DROPS,
                TargetClassifier.classify("minecraft:snow_golem", false, false, true));
        assertEquals(TargetClassifier.Kind.FRIENDLY_DROPS,
                TargetClassifier.classify("minecraft:copper_golem", false, false, true));

        assertEquals(TargetClassifier.Kind.FRIENDLY_NO_DROPS,
                TargetClassifier.classify("minecraft:villager", false, false, true));
        assertEquals(TargetClassifier.Kind.FRIENDLY_NO_DROPS,
                TargetClassifier.classify("minecraft:bat", false, false, true));
        assertEquals(TargetClassifier.Kind.FRIENDLY_NO_DROPS,
                TargetClassifier.classify("minecraft:camel", false, false, true));
        assertEquals(TargetClassifier.Kind.FRIENDLY_NO_DROPS,
                TargetClassifier.classify("minecraft:happy_ghast", false, false, true));
    }

    @Test
    void spiderDependsOnLightLevel() {
        assertEquals(TargetClassifier.Kind.NEUTRAL,
                TargetClassifier.classify("minecraft:spider", true, false, true));
        assertEquals(TargetClassifier.Kind.HOSTILE,
                TargetClassifier.classify("minecraft:spider", true, false, false));
        assertEquals(TargetClassifier.Kind.NEUTRAL,
                TargetClassifier.classify("minecraft:cave_spider", true, false, true));
        assertEquals(TargetClassifier.Kind.HOSTILE,
                TargetClassifier.classify("minecraft:cave_spider", true, false, false));
    }

    @Test
    void appliesVersion26Exceptions() {
        assertEquals(TargetClassifier.Kind.HOSTILE,
                TargetClassifier.classify("minecraft:zombie_nautilus", false, false, true));
        assertEquals(TargetClassifier.Kind.HOSTILE,
                TargetClassifier.classify("minecraft:sulfur_cube", false, false, true));
        assertEquals(TargetClassifier.Kind.NEUTRAL,
                TargetClassifier.classify("minecraft:mannequin", false, false, true));
    }

    @Test
    void fallsBackForUnknownMobs() {
        assertEquals(TargetClassifier.Kind.HOSTILE,
                TargetClassifier.classify("example:hostile", true, false, true));
        assertEquals(TargetClassifier.Kind.NEUTRAL,
                TargetClassifier.classify("example:neutral", false, true, true));
        assertEquals(TargetClassifier.Kind.FRIENDLY_DROPS,
                TargetClassifier.classify("example:friendly", false, false, true));
    }
}
