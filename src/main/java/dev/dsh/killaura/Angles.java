// SPDX-License-Identifier: LGPL-3.0-only
// Copyright (C) 2026 MineAgent

package dev.dsh.killaura;

import net.minecraft.world.phys.Vec3;

/** Angle helpers shared by target selection and the attack rotation. */
public final class Angles {
    private Angles() {
    }

    /**
     * Rotation (yaw, pitch) that points from {@code from} to {@code to}.
     *
     * <p>The yaw is unwrapped relative to {@code currentYaw}, so it never spins the long way
     * around when the target lies across the -180/180 boundary. Pitch is clamped to vanilla's
     * legal range.
     */
    public static float[] lookAt(Vec3 from, Vec3 to, float currentYaw) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        float yawDelta = wrapDegrees(yaw - currentYaw);

        return new float[] {currentYaw + yawDelta, Math.max(-90.0F, Math.min(90.0F, pitch))};
    }

    /**
     * Angle in degrees between the player's view direction and the direction from {@code eyes} to
     * {@code target}. 0 means looking straight at it, 180 means it is directly behind.
     */
    public static double offAxisDegrees(float yawDegrees, float pitchDegrees, Vec3 eyes, Vec3 target) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);

        double lookX = -Math.sin(yaw) * Math.cos(pitch);
        double lookY = -Math.sin(pitch);
        double lookZ = Math.cos(yaw) * Math.cos(pitch);

        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-6) {
            return 0.0;
        }

        double dot = (lookX * dx + lookY * dy + lookZ * dz) / length;
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
    }

    /** Wraps an angle into [-180, 180). */
    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }
}
