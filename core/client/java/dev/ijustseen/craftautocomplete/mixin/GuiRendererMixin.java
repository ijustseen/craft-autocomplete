package dev.ijustseen.craftautocomplete.mixin;

import dev.ijustseen.craftautocomplete.ui.GhostFade;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
abstract class GuiRendererMixin {
    @Unique
    private GuiItemRenderState craftautocomplete$item;

    @Inject(method = "submitBlitFromItemAtlas", at = @At("HEAD"))
    private void rememberItem(GuiItemRenderState state, GuiItemAtlas.SlotView slot, CallbackInfo callback) {
        craftautocomplete$item = state;
    }

    @ModifyConstant(method = "submitBlitFromItemAtlas", constant = @Constant(intValue = -1))
    private int fadeGhostItem(int color) {
        GuiItemRenderState state = craftautocomplete$item;
        if (state != null && GhostFade.isGhost(state.itemStackRenderState())) {
            return GhostFade.TINT;
        }
        return color;
    }
}
