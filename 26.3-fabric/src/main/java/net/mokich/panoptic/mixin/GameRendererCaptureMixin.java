package net.mokich.panoptic.mixin;

import net.mokich.panoptic.event.ScreenCaptureHandler;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererCaptureMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void panoptic$captureFrame(CallbackInfo ci) {
        ScreenCaptureHandler.onFrameEnd();
    }
}
