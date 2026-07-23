package net.mokich.panoptic.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.mokich.panoptic.event.InteractionEventHandler;
import net.mokich.panoptic.event.ScreenCaptureHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenKeyboardMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void panoptic$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        Screen self = (Screen) (Object) this;
        if (InteractionEventHandler.onScreenKey(self, keyCode) || ScreenCaptureHandler.onScreenKey(self, keyCode)) {
            cir.setReturnValue(true);
        }
    }
}