package com.autohotbar.client.mixin;

import com.autohotbar.client.AutoHotbarClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class ContainerScreenMixin {
    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void onExtractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if ((Object) this instanceof CreativeModeInventoryScreen) return;
        AutoHotbarClient.Renderer.renderContainerSlot(graphics, slot);
    }
}