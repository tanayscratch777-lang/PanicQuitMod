package com.example.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class ExampleModClient implements ClientModInitializer {
    public static final KeyMapping.Category PANIC_CATEGORY = KeyMapping.Category.register(
        Identifier.fromNamespaceAndPath("panicmod", "panic")
    );

    public static KeyMapping panicKey;

    @Override
    public void onInitializeClient() {
        panicKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.panicmod.trigger",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            PANIC_CATEGORY
        ));
    }

    public static boolean checkAndTrigger(KeyEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || panicKey == null) return false;

        boolean keyMatches = panicKey.matches(event);
        boolean altHeld = InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_ALT);

        if (keyMatches && altHeld) {
            long window = client.getWindow().handle();

            // 1. Instantly hide the window
            GLFW.glfwIconifyWindow(window);

            // 2. If NOT in a world (title screen, options, etc.), close INSTANTLY
            if (client.level == null) {
                System.exit(0);
                return true;
            }

            // 3. If in a world / server, safely save and disconnect first
            new Thread(() -> {
                try {
                    // Tell singleplayer server to save all chunks cleanly
                    if (client.getSingleplayerServer() != null) {
                        client.getSingleplayerServer().halt(false);
                    }
                } catch (Exception ignored) {
                } finally {
                    // Clean exit without executor thread collisions
                    System.exit(0);
                }
            }, "PanicQuit-Shutdown").start();

            return true;
        }

        return false;
    }
}