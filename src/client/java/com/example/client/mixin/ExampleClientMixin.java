package com.example.client.mixin;

import com.example.client.ExampleModClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AbstractContainerScreen.class, KeyboardHandler.class})
public class ExampleClientMixin {

    // 1. Injects into inventory container slots to render emerald borders & key badges
    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void onExtractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ExampleModClient.Renderer.renderContainerSlot(graphics, slot);
    }

    // 2. Injects into keyboard handler to trigger the Panic Button (Left Alt + I) universally
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKeyPress(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS) {
            if (ExampleModClient.checkPanic(key, scancode, modifiers)) {
                ci.cancel();
            }
        }
    }
}