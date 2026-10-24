package net.mokich.panoptic.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiGraphicsExtractor.class)
public interface GuiRenderStateAccessor {
    @Accessor("guiRenderState")
    GuiRenderState panoptic$guiRenderState();
}