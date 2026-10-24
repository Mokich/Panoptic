package net.mokich.panoptic.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.mokich.panoptic.event.InteractionEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void panoptic$onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (InteractionEventHandler.onMouse(net.mokich.panoptic.api.util.MouseButtons.legacy(info.button()), action)) {
            ci.cancel();
        }
    }
}
