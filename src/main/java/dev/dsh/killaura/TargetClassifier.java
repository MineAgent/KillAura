// SPDX-License-Identifier: LGPL-3.0-only
// Copyright (C) 2026 MineAgent

package dev.dsh.killaura;

import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Classifies a living entity for target selection.
 *
 * <p>Only client-available data is used: the entity class, {@link Enemy} / {@link NeutralMob}
 * markers, the current light level (for spiders) and a small set of 26.2-specific exceptions.
 * The server's current AI target is not synchronized to the client, so "is hostile right now"
 * cannot be answered exactly; conditional mobs are therefore treated as neutral.
 */
public final class TargetClassifier {
    private TargetClassifier() {
    }

    public enum Kind {
        HOSTILE,
        NEUTRAL,
        FRIENDLY_DROPS,
        FRIENDLY_NO_DROPS
    }

    /**
     * Mobs that can attack players only under a condition (provoked, no gold armor, etc.).
     * They are neutral, so none of the three attack switches targets them by default.
     */
    private static final Set<String> CONDITIONAL = Set.of(
            "minecraft:piglin",
            "minecraft:llama",
            "minecraft:trader_llama",
            "minecraft:panda",
            "minecraft:goat",
            "minecraft:pufferfish");

    /**
     * 26.2 mobs that are hostile without implementing {@link Enemy} (brain-based AI).
     */
    private static final Set<String> FORCED_HOSTILE = Set.of(
            "minecraft:zombie_nautilus",
            "minecraft:sulfur_cube");

    /**
     * Decorative living entities that should not be picked up by the normal target switches.
     */
    private static final Set<String> FORCED_NEUTRAL = Set.of(
            "minecraft:mannequin");

    /**
     * Same light check as vanilla's {@code Spider.SpiderTargetGoal}: bright light makes spiders
     * stop actively targeting players, dark light makes them hostile.
     */
    private static final Set<String> LIGHT_DEPENDENT = Set.of(
            "minecraft:spider",
            "minecraft:cave_spider");

    /**
     * Friendly entities whose 26.2 entity loot table has no pools, i.e. they never drop loot.
     * Extracted from {@code data/minecraft/loot_table/entities/*.json} in the 26.2 client jar.
     * Player-supplied equipment (saddles, horse armor, harnesses, ...) is not considered.
     */
    private static final Set<String> FRIENDLY_NO_DROPS = Set.of(
            "minecraft:allay",
            "minecraft:armadillo",
            "minecraft:axolotl",
            "minecraft:bat",
            "minecraft:camel",
            "minecraft:fox",
            "minecraft:frog",
            "minecraft:happy_ghast",
            "minecraft:ocelot",
            "minecraft:sniffer",
            "minecraft:tadpole",
            "minecraft:villager",
            "minecraft:wandering_trader");

    @SuppressWarnings("deprecation")
    public static Kind classify(LivingEntity entity) {
        return classify(
                id(entity),
                entity instanceof Enemy,
                entity instanceof NeutralMob,
                entity.getLightLevelDependentMagicValue() >= 0.5F);
    }

    static Kind classify(String id, boolean enemy, boolean neutralMob, boolean bright) {
        if (LIGHT_DEPENDENT.contains(id)) {
            return bright ? Kind.NEUTRAL : Kind.HOSTILE;
        }
        if (FORCED_HOSTILE.contains(id)) {
            return Kind.HOSTILE;
        }
        if (neutralMob || CONDITIONAL.contains(id) || FORCED_NEUTRAL.contains(id)) {
            return Kind.NEUTRAL;
        }
        if (enemy) {
            return Kind.HOSTILE;
        }
        return FRIENDLY_NO_DROPS.contains(id) ? Kind.FRIENDLY_NO_DROPS : Kind.FRIENDLY_DROPS;
    }

    public static String id(Entity entity) {
        return EntityType.getKey(entity.getType()).toString();
    }
}
