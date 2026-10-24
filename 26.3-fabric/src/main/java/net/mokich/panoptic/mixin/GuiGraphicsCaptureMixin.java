package net.mokich.panoptic.mixin;

import net.mokich.panoptic.screenshot.GuiCaptureRecorder;
import net.mokich.panoptic.screenshot.HoverProbe;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
public class GuiGraphicsCaptureMixin {
    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD"), ordinal = 2, argsOnly = true, require = 0)
    private int panoptic$forceTextAlpha(int color) {
        return (color & 0xFF000000) == 0 ? (color | 0xFF000000) : color;
    }

    private Matrix3x2f panoptic$matrix() {
        return new Matrix3x2f(((GuiGraphicsExtractor) (Object) this).pose());
    }

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At("HEAD"), require = 0)
    private void panoptic$item(LivingEntity entity, Level level, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        Matrix3x2f m = panoptic$matrix();
        if (GuiCaptureRecorder.isArmed()) {
            GuiCaptureRecorder.itemAt(stack, m, x, y);
        }
        if (HoverProbe.tracking()) {
            HoverProbe.itemAt(stack, m, x, y);
        }
    }

    @Inject(method = "fill(IIIII)V", at = @At("HEAD"), require = 0)
    private void panoptic$fill(int x1, int y1, int x2, int y2, int color, CallbackInfo ci) {
        if (GuiCaptureRecorder.isArmed()) {
            GuiCaptureRecorder.fill(panoptic$matrix(), x1, y1, x2, y2, color);
        }
    }

    @Inject(method = "fillGradient(IIIIII)V", at = @At("HEAD"), require = 0)
    private void panoptic$gradient(int x1, int y1, int x2, int y2, int from, int to, CallbackInfo ci) {
        if (GuiCaptureRecorder.isArmed()) {
            GuiCaptureRecorder.gradient(panoptic$matrix(), x1, y1, x2, y2, from, to);
        }
    }

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V", at = @At("HEAD"), require = 0)
    private void panoptic$drawStr(Font font, String text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        if (GuiCaptureRecorder.isArmed()) {
            GuiCaptureRecorder.text(panoptic$matrix(), text, x, y, color, shadow);
        }
    }

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD"), require = 0)
    private void panoptic$drawSeq(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        if (GuiCaptureRecorder.isArmed()) {
            GuiCaptureRecorder.text(panoptic$matrix(), panoptic$plain(text), x, y, color, shadow);
        }
    }

    @Inject(method = "enableScissor(IIII)V", at = @At("HEAD"), require = 0)
    private void panoptic$scissor(int x1, int y1, int x2, int y2, CallbackInfo ci) {
        if (GuiCaptureRecorder.isArmed()) {
            GuiCaptureRecorder.scissor(x1, y1, x2, y2);
        }
    }

    @Inject(method = "disableScissor()V", at = @At("HEAD"), require = 0)
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