package com.example.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class ExampleModClient implements ClientModInitializer {
    public static KeyMapping panicKey;

    @Override
    public void onInitializeClient() {
        panicKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.panicmod.trigger",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            KeyMapping.Category.MISC
        ));
    }

    public static boolean checkAndTrigger(int key, int scancode, int modifiers) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || panicKey == null) return false;

        boolean keyMatches = panicKey.matches(key, scancode);
        long window = client.getWindow().getWindow();
        boolean altHeld = (modifiers & GLFW.GLFW_MOD_ALT) != 0 
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT);

        if (keyMatches && altHeld) {
            GLFW.glfwIconifyWindow(window);
            client.stop();
            return true;
        }

        return false;
    }
}