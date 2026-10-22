
package com.colorwheel.client;

import com.colorwheel.ColorMap;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

public class ColorWheelClient implements ClientModInitializer {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath("colorwheel", "main")
            );

    private static final KeyMapping MENU_KEY =
            KeyMappingHelper.registerKeyMapping(
                    new KeyMapping(
                            "key.colorwheel.menu",
                            KeyMapping.Type.KEYSYM,
                            75,
                            CATEGORY
                    )
            );

    @Override
    public void onInitializeClient() {
        ColorWheelHud.register();

        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
                ColorMap.initializeTextures(client.getResourceManager())
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (MENU_KEY.consumeClick()) {
                if (client.player != null) {
                    client.gui.setScreen(new ConfigScreen());
                }
            }
        });
    }
}
