package net.mokich.panoptic.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.mokich.panoptic.screenshot.GuiCaptureRecorder;
import net.mokich.panoptic.screenshot.HoverProbe;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public class ItemRendererCaptureMixin {
    @Inject(
            method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At("HEAD")
    )
    private void panoptic$guiItem(ItemStack stack, ItemDisplayContext ctx, boolean leftHand, PoseStack pose,
                                         MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (ctx == ItemDisplayContext.GUI) {
            if (GuiCaptureRecorder.isArmed()) {
                GuiCaptureRecorder.itemAbs(stack, pose.last().pose());
            }
            if (HoverProbe.tracking()) {
                HoverProbe.item(stack, pose.last().pose());
            }
        }
    }
}