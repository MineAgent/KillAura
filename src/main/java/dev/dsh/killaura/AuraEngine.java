// SPDX-License-Identifier: LGPL-3.0-only
// Copyright (C) 2026 MineAgent

package dev.dsh.killaura;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Per-tick state machine: target acquisition, aiming and attacking. */
public final class AuraEngine {
    public static final String MOD_ID = "killaura-reimpl";
    private static final Logger LOG = LoggerFactory.getLogger("Kill Aura");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path configFile;

    private AuraSettings settings;
    private boolean active;
    private String lastTargetName;
    private long lastAttackAt;
    private boolean firstRotationLogged;
    private boolean rotationErrorLogged;

    public AuraEngine(Path configFile) {
        this.configFile = configFile;
        this.settings = AuraSettings.fromFile(configFile);
    }

    public boolean isActive() {
        return active;
    }

    public void onTick(Minecraft mc) {
        if (!active) {
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        if (settings.pauseInScreens && mc.gui.screen() != null) {
            return;
        }
        if (settings.pauseWhileUsingItem && player.isUsingItem()) {
            return;
        }
        if (player.getAttackStrengthScale(0.0F) < (float) settings.minAttackCharge) {
            return;
        }

        LivingEntity target = TargetFilter.pick(mc, player, settings);
        if (target == null) {
            return;
        }

        Vec3 eyes = player.getEyePosition();
        Vec3 aim = target.getBoundingBox().getCenter();
        float[] rotation = Angles.lookAt(eyes, aim, player.getYRot());

        switch (settings.rotation) {
            case "silent" -> sendRotation(player, rotation);
            case "visible" -> {
                player.setYRot(rotation[0]);
                player.setXRot(rotation[1]);
            }
            default -> {
                // "none": attack without turning at all.
            }
        }

        mc.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);

        lastTargetName = target.getName().getString();
        lastAttackAt = System.currentTimeMillis();
    }

    /** Turns the aura on or off. Enabling reloads the settings file, like the original toggle did. */
    public void setEnabled(boolean enabled) {
        if (active == enabled) {
            return;
        }

        active = enabled;
        if (enabled) {
            settings = AuraSettings.fromFile(configFile);
        }

        LOG.info(
                "Kill Aura {} (range {}, rotation {}, priority {}, players {}, hostile {}, friendlyDrops {},"
                        + " friendlyNoDrops {}, deny {}, always {})",
                active ? "ON" : "OFF",
                settings.range,
                settings.rotation,
                settings.priority,
                settings.targetPlayers,
                settings.attackHostile,
                settings.attackFriendlyDrops,
                settings.attackFriendlyNoDrops,
                settings.denyTargets.size(),
                settings.alwaysTargets.size());
    }

    public void toggle() {
        setEnabled(!active);
    }

    /** Re-reads {@code config/killaura.json}; takes effect immediately, even while enabled. */
    public void reloadSettings() {
        settings = AuraSettings.fromFile(configFile);
        LOG.info("Kill Aura settings reloaded (range {}, rotation {}, priority {})",
                settings.range, settings.rotation, settings.priority);
    }

    /**
     * Applies a strict partial config patch and writes the file. Must run on the client thread so
     * the tick loop never observes a half-applied configuration.
     *
     * @return validation errors; when non-empty nothing was changed
     */
    public List<String> applyConfigPatch(JsonObject patch) {
        AuraSettings candidate = settings.copy();
        List<String> errors = candidate.applyPatch(patch);
        if (!errors.isEmpty()) {
            return errors;
        }

        settings = candidate;
        settings.toFile(configFile);
        LOG.info("Kill Aura config updated (range {}, rotation {}, priority {})",
                settings.range, settings.rotation, settings.priority);
        return List.of();
    }

    /** Immutable view of the live state for the HTTP endpoint. Must run on the client thread. */
    public AuraSnapshot snapshot() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc == null ? null : mc.player;
        boolean inWorld = player != null && mc.level != null;

        JsonObject config = GSON.toJsonTree(settings, AuraSettings.class).getAsJsonObject();
        config.remove("_help");

        AuraSnapshot.Target target = null;
        if (active && inWorld) {
            LivingEntity picked = TargetFilter.pick(mc, player, settings);
            if (picked != null) {
                double distance = TargetFilter.gapToBox(player.getEyePosition(), picked.getBoundingBox());
                target = new AuraSnapshot.Target(
                        picked.getName().getString(),
                        picked.getId(),
                        Math.round(distance * 100.0) / 100.0,
                        picked.getHealth());
            }
        }

        AuraSnapshot.LastAttack lastAttack = lastTargetName == null
                ? null
                : new AuraSnapshot.LastAttack(lastTargetName, Math.max(0L, System.currentTimeMillis() - lastAttackAt));

        return new AuraSnapshot(
                active,
                inWorld,
                player == null ? null : player.getName().getString(),
                config,
                target,
                lastAttack);
    }

    /**
     * Sends a rotation-only movement packet. The server treats this as the player's new look
     * direction, which is what makes the hit land from the aimed angle while the client view
     * stays untouched.
     */
    private void sendRotation(LocalPlayer player, float[] rotation) {
        if (player.connection == null) {
            return;
        }

        try {
            player.connection.send(new ServerboundMovePlayerPacket.Rot(
                    rotation[0], rotation[1], player.onGround(), player.horizontalCollision));

            if (!firstRotationLogged) {
                firstRotationLogged = true;
                LOG.info("Silent rotation active (first packet: yaw {}, pitch {})", rotation[0], rotation[1]);
            }
        } catch (Throwable t) {
            if (!rotationErrorLogged) {
                rotationErrorLogged = true;
                LOG.warn("Could not send rotation packet", t);
            }
        }
    }
}
