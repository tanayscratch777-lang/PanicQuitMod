package com.example.client.mixin;

import com.example.client.ExampleModClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class ExampleClientMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    public void onKeyPress(long handle, int action, KeyEvent event, CallbackInfo ci) {
        if (action == 1) { // 1 = GLFW_PRESS
            if (ExampleModClient.checkAndTrigger(event)) {
                ci.cancel();
            }
        }
    }
}