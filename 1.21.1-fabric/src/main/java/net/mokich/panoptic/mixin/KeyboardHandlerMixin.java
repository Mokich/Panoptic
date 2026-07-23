package net.mokich.panoptic.mixin;

import net.minecraft.client.KeyboardHandler;
import net.mokich.panoptic.event.InteractionEventHandler;
import net.mokich.panoptic.event.ScreenCaptureHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void panoptic$keyPress(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        InteractionEventHandler.onKey(key, action);
        ScreenCaptureHandler.onWorldKey(key, action);
    }
}