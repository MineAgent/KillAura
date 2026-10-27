// SPDX-License-Identifier: LGPL-3.0-only
// Copyright (C) 2026 MineAgent

package dev.dsh.killaura;

import com.example.httpd.HttpdProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric client entrypoint: starts the tick hook and mounts {@code /ka} on MGHttpdProvider's
 * shared server (127.0.0.1:3420). There is no key binding; the mod is controlled over HTTP.
 */
public final class AuraClient implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("Kill Aura");

    @Override
    public void onInitializeClient() {
        AuraEngine engine = new AuraEngine(FabricLoader.getInstance().getConfigDir().resolve("killaura.json"));

        ClientTickEvents.END_CLIENT_TICK.register(engine::onTick);
        HttpdProvider.register(AuraEndpoint.PREFIX, AuraEndpoint.NAME, AuraEndpoint.ENDPOINTS, new AuraEndpoint(engine));

        LOG.info("Kill Aura (reimpl) loaded - control via http://{}:{}{}",
                HttpdProvider.HOST, HttpdProvider.PORT, AuraEndpoint.PREFIX);
    }
}
