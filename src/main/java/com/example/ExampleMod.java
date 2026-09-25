package com.panicmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class PanicClientMod implements ClientModInitializer {
    public static final String MOD_ID = "panicmod";
    public static KeyBinding panicKey;

    @Override
    public void onInitializeClient() {
        // Registers in Minecraft Controls menu -> Keybinds (Default: 'I')
        panicKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.panicmod.trigger",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            "category.panicmod"
        ));
    }

    // Called universally from the Mixin whenever any key is pressed
    public static boolean checkAndTrigger(int key, int scancode, int modifiers) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || panicKey == null) return false;

        // Check if the pressed key matches the configured panic key
        boolean keyMatches = panicKey.matchesKey(key, scancode);

        // Check if Left Alt is also held down
        long window = client.getWindow().getHandle();
        boolean altHeld = (modifiers & GLFW.GLFW_MOD_ALT) != 0 
                || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_ALT);

        if (keyMatches && altHeld) {
            triggerPanic(client);
            return true;
        }

        return false;
    }

    private static void triggerPanic(MinecraftClient client) {
        long windowHandle = client.getWindow().getHandle();

        // 1. Instantly minimize the Minecraft window to the taskbar
        GLFW.glfwIconifyWindow(windowHandle);

        // 2. Safe shutdown: cleanly saves worlds/disconnects servers, then terminates
        client.scheduleStop();
    }
}