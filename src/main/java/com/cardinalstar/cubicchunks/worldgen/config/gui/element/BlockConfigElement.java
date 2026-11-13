package com.cardinalstar.cubicchunks.worldgen.config.gui.element;

import com.cardinalstar.cubicchunks.worldgen.config.BlockRef;
import com.cardinalstar.cubicchunks.worldgen.config.WGConfigItem;
import com.cardinalstar.cubicchunks.worldgen.config.WGDimensionConfig;
import com.cardinalstar.cubicchunks.worldgen.config.gui.BlockVariants;
import com.cardinalstar.cubicchunks.worldgen.config.gui.entry.BlockSelectorEntry;

import cpw.mods.fml.client.config.ConfigGuiType;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class BlockConfigElement extends WGConfigElement<BlockRef> {

    public BlockConfigElement(WGConfigItem<BlockRef> item, WGDimensionConfig sandbox) {
        super(item, sandbox, ConfigGuiType.STRING);
        setCustomListEntryClass(BlockSelectorEntry.class);
    }

    @Override
    protected String formatValue(BlockRef value) {
        return BlockVariants.label(value);
    }
}
