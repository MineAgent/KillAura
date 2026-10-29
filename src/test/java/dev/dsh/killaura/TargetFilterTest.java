package dev.dsh.killaura;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TargetFilterTest {
    @Test
    void forcedTargetsBeatBetterScoringNormalTargets() {
        assertTrue(TargetFilter.isBetter(true, 100.0, false, 1.0));
        assertFalse(TargetFilter.isBetter(false, 1.0, true, 100.0));
    }

    @Test
    void sameClassUsesLowerScore() {
        assertTrue(TargetFilter.isBetter(true, 1.0, true, 2.0));
        assertTrue(TargetFilter.isBetter(false, 1.0, false, 2.0));
        assertFalse(TargetFilter.isBetter(false, 2.0, false, 1.0));
    }
}
