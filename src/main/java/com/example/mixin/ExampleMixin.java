package com.example.mixin;

import com.example.ExampleMod;
import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class ExampleMixin {

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKeyInject(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS) {
            if (ExampleMod.checkAndTrigger(key, scancode, modifiers)) {
                ci.cancel();
            }
        }
    }
}