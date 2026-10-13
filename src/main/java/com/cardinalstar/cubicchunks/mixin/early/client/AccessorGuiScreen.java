package com.cardinalstar.cubicchunks.mixin.early.client;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiScreen.class)
public interface AccessorGuiScreen {

    @Accessor
    FontRenderer getFontRendererObj();

}
