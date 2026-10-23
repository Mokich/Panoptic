package net.mokich.panoptic.mixin;

import net.mokich.panoptic.screenshot.GuiCaptureRecorder;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiGraphics.class)
public class GuiGraphicsCaptureMixin {
    private Matrix4f panoptic$matrix() {
        return ((GuiGraphics) (Object) this).pose().last().pose();
    }

    @Inject(
            method = "innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V",
            at = @At("HEAD")
    )
    private void panoptic$blit(ResourceLocation tex, int x1, int x2, int y1, int y2, int blitOffset,
                                      float minU, float maxU, float minV, float maxV, CallbackInfo ci) {
        if (GuiCaptureRecorder.isArmed()) {
            GuiCaptureRecorder.blit(panoptic$matrix(), tex, x1, x2, y1, y2, minU, maxU, minV, maxV);
        }
    }

    @Inject(
            method = "fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V",
            at = @At("HEAD")
    )
    private void panoptic$fill(RenderType type, int x1, int y1, int x2, int y2, int z, int color, CallbackInfo ci) {
        if (GuiCaptureRecorder.isArmed()) {
            GuiCaptureRecorder.fill(panoptic$matrix(), x1, y1, x2, y2, color);
        }
    }

    @Inject(
            method = "fillGradient(Lnet/minecraft/client/renderer/RenderType;IIIIIII)V",
            at = @At("HEAD")
    )
    private void panoptic$gradient(RenderType type, int x1, int y1, int x2, int y2, int z,
                                          int from, int to, CallbackInfo ci) {
        if (GuiCaptureRecorder.isArmed()) {
            GuiCaptureRecorder.gradient(panoptic$matrix(), x1, y1, x2, y2, from, to);
        }
    }

    @Inject(
            method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
            at = @At("HEAD")
    )
    private void panoptic$drawStr(Font font, String text, int x, int y, int color, boolean shadow,
                                         CallbackInfoReturnable<Integer> cir) {
        if (GuiCaptureRecorder.isArmed()) {
            GuiCaptureRecorder.text(panoptic$matrix(), text, x, y, color, shadow);
        }
    }

    @Inject(
            method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I",
            at = @At("HEAD")
    )
    private void panoptic$drawSeq(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow,
                                         CallbackInfoReturnable<Integer> cir) {
        if (GuiCaptureRecorder.isArmed()) {
            GuiCaptureRecorder.text(panoptic$matrix(), panoptic$plain(text), x, y, color, shadow);
        }
    }

    @Inject(method = "enableScissor(IIII)V", at = @At("HEAD"))
    private void panoptic$scissor(int x1, int y1, int x2, int y2, CallbackInfo ci) {
        if (GuiCaptureRecorder.isArmed()) {
            GuiCaptureRecorder.scissor(x1, y1, x2, y2);
        }
    }

    @Inject(method = "disableScissor()V", at = @At("HEAD"))
    private void panoptic$unscissor(CallbackInfo ci) {
        if (GuiCaptureRecorder.isArmed()) {
            GuiCaptureRecorder.unscissor();
        }
    }

    private static String panoptic$plain(FormattedCharSequence seq) {
        StringBuilder sb = new StringBuilder();
        seq.accept((idx, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }
}