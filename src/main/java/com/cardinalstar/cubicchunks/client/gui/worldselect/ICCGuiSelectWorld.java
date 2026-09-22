package com.cardinalstar.cubicchunks.client.gui.worldselect;

import org.jetbrains.annotations.ApiStatus;

/**
 * Injected onto {@link net.minecraft.client.gui.GuiSelectWorld} by
 * {@code MixinGuiSelectWorld}. Provides controlled access to CC-added state.
 *
 * @see MixinGuiSelectWorld
 */
@ApiStatus.Internal
public interface ICCGuiSelectWorld {

    /** Sets the flag that suppresses the vanilla-format warning on the next load attempt. */
    void cubicchunks$setBypassFormatWarning(boolean value);

    /** Triggers the vanilla world-load flow for the given list index. */
    void cubicchunks$loadWorld(int index);
}
