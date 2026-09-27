package com.example.client.mixin;

import com.example.client.AutoHotbarClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class ContainerScreenMixin {
    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void onRenderSlot(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        // Ignores creative mode so tabs never glitch
        if ((Object) this instanceof CreativeModeInventoryScreen) return;
        AutoHotbarClient.Renderer.renderContainerSlot(graphics, slot);
    }
}