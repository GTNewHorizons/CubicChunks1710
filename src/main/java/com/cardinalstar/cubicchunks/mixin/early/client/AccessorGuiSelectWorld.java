package com.cardinalstar.cubicchunks.mixin.early.client;

import net.minecraft.client.gui.GuiSelectWorld;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiSelectWorld.class)
public interface AccessorGuiSelectWorld {

    @Invoker
    String invokeFunc_146621_a(int p_146621_1_);

}
