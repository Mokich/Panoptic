package net.mokich.panoptic.mixin;

import net.minecraft.client.MouseHandler;
import net.mokich.panoptic.event.InteractionEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void panoptic$onPress(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (InteractionEventHandler.onMouse(button, action)) {
            ci.cancel();
        }
    }
}