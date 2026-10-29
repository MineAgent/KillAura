// SPDX-License-Identifier: LGPL-3.0-only
// Copyright (C) 2026 MineAgent

package dev.dsh.killaura;

/**
 * The rule order for a single candidate.
 *
 * <p>{@code denyTargets} beats everything, any of the four {@code alwaysTargets*} lists beats the
 * normal filters, then the per-type toggles apply, and finally the hostile/friendly classification
 * decides. The four always tiers only affect which of several legal targets is selected.
 */
public final class TargetRules {
    private TargetRules() {
    }

    enum Decision {
        DENY,
        NORMAL,
        FORCE
    }

    /** Selection order; declaration order is the priority order. */
    enum Tier {
        ALWAYS_HIGH,
        HOSTILE,
        ALWAYS_MEDIUM,
        FRIENDLY_DROPS,
        ALWAYS_LOW,
        FRIENDLY_NO_DROPS,
        ALWAYS_LOWEST
    }

    static boolean isAlwaysTarget(String id, AuraSettings settings) {
        return settings.alwaysTargetsHigh.contains(id)
                || settings.alwaysTargetsMedium.contains(id)
                || settings.alwaysTargetsLow.contains(id)
                || settings.alwaysTargetsLowest.contains(id);
    }

    /**
     * Tier used to choose between legal targets, or {@code null} when the entity has no legal
     * tier under the current switches. A target in several always lists uses the highest one.
     *
     * <p>Players and armor stands are not classified as hostile/friendly. When they are not in an
     * always list they use the friendly-no-drops tier; whether they may be attacked is still
     * controlled solely by {@code targetPlayers} / {@code targetArmorStands}.
     */
    static Tier tierFor(String id, TargetClassifier.Kind kind, boolean player, boolean armorStand,
                        AuraSettings settings) {
        if (settings.alwaysTargetsHigh.contains(id)) {
            return Tier.ALWAYS_HIGH;
        }
        if (settings.alwaysTargetsMedium.contains(id)) {
            return Tier.ALWAYS_MEDIUM;
        }
        if (settings.alwaysTargetsLow.contains(id)) {
            return Tier.ALWAYS_LOW;
        }
        if (settings.alwaysTargetsLowest.contains(id)) {
            return Tier.ALWAYS_LOWEST;
        }

        if (player) {
            return settings.targetPlayers ? Tier.FRIENDLY_NO_DROPS : null;
        }
        if (armorStand) {
            return settings.targetArmorStands ? Tier.FRIENDLY_NO_DROPS : null;
        }

        return switch (kind) {
            case HOSTILE -> settings.attackHostile ? Tier.HOSTILE : null;
            case FRIENDLY_DROPS -> settings.attackFriendlyDrops ? Tier.FRIENDLY_DROPS : null;
            case FRIENDLY_NO_DROPS -> settings.attackFriendlyNoDrops ? Tier.FRIENDLY_NO_DROPS : null;
            case NEUTRAL -> null;
        };
    }

    static Decision evaluate(String id, boolean player, boolean armorStand, boolean invisible,
                             boolean lineOfSight, TargetClassifier.Kind kind, AuraSettings settings) {
        if (settings.denyTargets.contains(id)) {
            return Decision.DENY;
        }
        if (isAlwaysTarget(id, settings)) {
            return Decision.FORCE;
        }

        if (settings.ignoreInvisible && invisible) {
            return Decision.DENY;
        }
        if (player) {
            return settings.targetPlayers ? Decision.NORMAL : Decision.DENY;
        }
        if (armorStand) {
            return settings.targetArmorStands ? Decision.NORMAL : Decision.DENY;
        }
        if (!settings.throughWalls && !lineOfSight) {
            return Decision.DENY;
        }

        return switch (kind) {
            case HOSTILE -> settings.attackHostile ? Decision.NORMAL : Decision.DENY;
            case FRIENDLY_DROPS -> settings.attackFriendlyDrops ? Decision.NORMAL : Decision.DENY;
            case FRIENDLY_NO_DROPS -> settings.attackFriendlyNoDrops ? Decision.NORMAL : Decision.DENY;
            case NEUTRAL -> Decision.DENY;
        };
    }
}
