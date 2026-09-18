package com.cardinalstar.cubicchunks.mixin.early.client;

import java.util.List;

import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.world.storage.SaveFormatComparator;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accessor mixin for private fields in {@link GuiSelectWorld}. */
@ApiStatus.Internal
@Mixin(GuiSelectWorld.class)
public interface IGuiSelectWorld {

    /** The full list of save-format descriptors, one per world folder. */
    @Accessor("field_146639_s")
    List<SaveFormatComparator> getWorldList();
}
