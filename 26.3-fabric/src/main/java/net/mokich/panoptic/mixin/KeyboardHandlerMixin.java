package net.mokich.panoptic.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import net.mokich.panoptic.event.InteractionEventHandler;
import net.mokich.panoptic.event.ScreenCaptureHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void panoptic$keyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        InteractionEventHandler.onKey(event.key(), action);
        ScreenCaptureHandler.onWorldKey(event.key(), action);
    }
}
