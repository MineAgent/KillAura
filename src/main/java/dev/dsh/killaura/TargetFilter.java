// SPDX-License-Identifier: LGPL-3.0-only
// Copyright (C) 2026 MineAgent

package dev.dsh.killaura;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Finds the single best attack target for one tick, or {@code null}. */
public final class TargetFilter {
    private TargetFilter() {
    }

    public static LivingEntity pick(Minecraft mc, LocalPlayer self, AuraSettings settings) {
        if (mc.level == null) {
            return null;
        }

        Vec3 eyes = self.getEyePosition();
        AABB searchArea = self.getBoundingBox().inflate(settings.range + 2.0);
        EntityTypeTest<Entity, LivingEntity> living = EntityTypeTest.forClass(LivingEntity.class);
        List<LivingEntity> nearby = mc.level.getEntities(living, searchArea, e -> eligible(self, e, settings));

        LivingEntity best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (LivingEntity candidate : nearby) {
            AABB box = candidate.getBoundingBox();
            double gap = gapToBox(eyes, box);
            if (gap > settings.range) {
                continue;
            }

            double offAxis = Angles.offAxisDegrees(self.getYRot(), self.getXRot(), eyes, box.getCenter());
            if (settings.fov < 360.0 && offAxis > settings.fov / 2.0) {
                continue;
            }
            if (!settings.throughWalls && !self.hasLineOfSight(candidate)) {
                continue;
            }

            double score = switch (settings.priority) {
                case "lowestHealth" -> candidate.getHealth() * 1000.0 + gap;
                case "angle" -> offAxis * 1000.0 + gap;
                default -> gap;
            };

            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }

    private static boolean eligible(LocalPlayer self, LivingEntity entity, AuraSettings settings) {
        if (entity == self || !entity.isAlive() || entity.isDeadOrDying() || entity.isRemoved()) {
            return false;
        }
        if (entity instanceof ArmorStand) {
            return settings.targetArmorStands;
        }
        if (settings.ignoreInvisible && entity.isInvisible()) {
            return false;
        }
        if (entity instanceof Player other) {
            if (other.isSpectator() || other.isCreative()) {
                return false;
            }
            return settings.targetPlayers;
        }
        return entity.getType().getCategory() == MobCategory.MONSTER
                ? settings.targetHostile
                : settings.targetPassive;
    }

    /** Shortest distance from a point to an axis-aligned box (0 when the point is inside). */
    static double gapToBox(Vec3 point, AABB box) {
        double dx = Math.max(Math.max(box.minX - point.x, 0.0), point.x - box.maxX);
        double dy = Math.max(Math.max(box.minY - point.y, 0.0), point.y - box.maxY);
        double dz = Math.max(Math.max(box.minZ - point.z, 0.0), point.z - box.maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
