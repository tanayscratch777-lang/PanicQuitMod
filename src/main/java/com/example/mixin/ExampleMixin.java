package com.panicmod.mixin;

import com.panicmod.PanicClientMod;
import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKeyInject(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        // Only trigger on key down/press
        if (action == GLFW.GLFW_PRESS) {
            if (PanicClientMod.checkAndTrigger(key, scancode, modifiers)) {
                ci.cancel(); // Prevent standard menu actions
            }
        }
    }
}