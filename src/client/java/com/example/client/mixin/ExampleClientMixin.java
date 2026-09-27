package com.example.client.mixin;

import com.example.client.ExampleModClient;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class ExampleClientMixin {
    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void onExtractSlot(GuiGraphicsExtractor graphics, Slot slot, CallbackInfo ci) {
        ExampleModClient.Renderer.renderContainerSlot(graphics, slot);
    }
}