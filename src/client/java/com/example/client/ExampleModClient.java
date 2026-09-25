package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ExampleModClient implements ClientModInitializer {
    public static KeyBinding panicKey;

    @Override
    public void onInitializeClient() {
        panicKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.panicmod.trigger",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            KeyBinding.Category.MISC
        ));
    }

    public static boolean checkAndTrigger(int key, int scancode, int modifiers) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || panicKey == null) return false;

        boolean keyMatches = panicKey.matchesKey(key, scancode);
        long window = client.getWindow().getHandle();
        boolean altHeld = (modifiers & GLFW.GLFW_MOD_ALT) != 0 
                || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_ALT);

        if (keyMatches && altHeld) {
            GLFW.glfwIconifyWindow(window);
            client.scheduleStop();
            return true;
        }

        return false;
    }
}