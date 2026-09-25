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
        Identifier.of("panicmod", "panic")
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
        long window = client.getWindow().getWindow();
        boolean altHeld = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;

        if (keyMatches && altHeld) {
            GLFW.glfwIconifyWindow(window);
            client.stop();
            return true;
        }

        return false;
    }
}