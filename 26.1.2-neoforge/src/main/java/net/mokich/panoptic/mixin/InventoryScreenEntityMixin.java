package net.mokich.panoptic.mixin;

import net.mokich.panoptic.screenshot.GuiCaptureRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Matrix3x2f;

@Mixin(InventoryScreen.class)
public class InventoryScreenEntityMixin {
    @Inject(
            method = "extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V",
            at = @At("HEAD"),
            require = 0
    )
    private static void panoptic$captureEntity(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2,
                                                      int size, float yOffset, float mouseX, float mouseY,
                                                      LivingEntity entity, CallbackInfo ci) {
        if (!GuiCaptureRecorder.isArmed() || entity == null) {
            return;
        }
        String type;
        if (entity == Minecraft.getInstance().player) {
            type = "self";
        } else {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            type = id == null ? "self" : id.toString();
        }
        int cx = (x1 + x2) / 2;
        int cy = (y1 + y2) / 2;
        GuiCaptureRecorder.entity(new Matrix3x2f(graphics.pose()), cx, cy, size, cx - mouseX, cy - mouseY, type);
    }
}