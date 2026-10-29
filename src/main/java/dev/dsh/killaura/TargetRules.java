// SPDX-License-Identifier: LGPL-3.0-only
// Copyright (C) 2026 MineAgent

package dev.dsh.killaura;

/**
 * The rule order for a single candidate.
 *
 * <p>{@code denyTargets} beats everything, {@code alwaysTargets} beats the normal filters and is
 * also preferred during selection, then the per-type toggles apply, and finally the
 * hostile/friendly classification decides.
 */
public final class TargetRules {
    private TargetRules() {
    }

    enum Decision {
        DENY,
        NORMAL,
        FORCE
    }

    static Decision evaluate(String id, boolean player, boolean armorStand, boolean invisible,
                             boolean lineOfSight, TargetClassifier.Kind kind, AuraSettings settings) {
        if (settings.denyTargets.contains(id)) {
            return Decision.DENY;
        }
        if (settings.alwaysTargets.contains(id)) {
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
