package dev.dsh.killaura;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TargetFilterTest {
    private static final TargetRules.Tier[] ORDER = {
            TargetRules.Tier.ALWAYS_HIGH,
            TargetRules.Tier.HOSTILE,
            TargetRules.Tier.ALWAYS_MEDIUM,
            TargetRules.Tier.FRIENDLY_DROPS,
            TargetRules.Tier.ALWAYS_LOW,
            TargetRules.Tier.FRIENDLY_NO_DROPS,
            TargetRules.Tier.ALWAYS_LOWEST
    };

    @Test
    void tierOrderMatchesTheConfiguredSelectionOrder() {
        for (int i = 0; i < ORDER.length; i++) {
            for (int j = i + 1; j < ORDER.length; j++) {
                assertTrue(TargetFilter.isBetter(ORDER[i], 100.0, ORDER[j], 1.0),
                        ORDER[i] + " should beat " + ORDER[j]);
                assertFalse(TargetFilter.isBetter(ORDER[j], 1.0, ORDER[i], 100.0),
                        ORDER[j] + " should not beat " + ORDER[i]);
            }
        }
    }

    @Test
    void sameTierUsesLowerScore() {
        assertTrue(TargetFilter.isBetter(TargetRules.Tier.ALWAYS_HIGH, 1.0,
                TargetRules.Tier.ALWAYS_HIGH, 2.0));
        assertTrue(TargetFilter.isBetter(TargetRules.Tier.FRIENDLY_DROPS, 1.0,
                TargetRules.Tier.FRIENDLY_DROPS, 2.0));
        assertFalse(TargetFilter.isBetter(TargetRules.Tier.ALWAYS_LOWEST, 2.0,
                TargetRules.Tier.ALWAYS_LOWEST, 1.0));
    }

    @Test
    void firstCandidateAlwaysWins() {
        assertTrue(TargetFilter.isBetter(TargetRules.Tier.ALWAYS_LOWEST, 100.0, null, 0.0));
    }
}
