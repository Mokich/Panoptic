package net.mokich.panoptic.mixin;

import net.mokich.panoptic.screenshot.GuiCaptureRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public class InventoryScreenEntityMixin {
    @Inject(
            method = "renderEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphics;IIIFFLnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD")
    )
    private static void panoptic$captureEntity(GuiGraphics graphics, int x, int y, int size,
                                                      float angleX, float angleY, LivingEntity entity, CallbackInfo ci) {
        if (!GuiCaptureRecorder.isArmed() || entity == null) {
            return;
        }
        String type;
        if (entity == Minecraft.getInstance().player) {
            type = "self";
        } else {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            type = id == null ? "self" : id.toString();
        }
        GuiCaptureRecorder.entity(graphics.pose().last().pose(), x, y, size, angleX, angleY, type);
    }
}