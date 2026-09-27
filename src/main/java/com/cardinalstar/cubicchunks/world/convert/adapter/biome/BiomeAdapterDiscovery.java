package com.cardinalstar.cubicchunks.world.convert.adapter.biome;

import net.minecraft.nbt.NBTTagCompound;

import com.cardinalstar.cubicchunks.world.convert.adapter.EIDSectionAdapter;
import com.cardinalstar.cubicchunks.world.convert.adapter.VanillaSectionAdapter;

public class BiomeAdapterDiscovery {

    private static final VanillaBiomeAdapter VANILLA = new VanillaBiomeAdapter();
    private static final EIDBiomeAdapter EID = new EIDBiomeAdapter();

    /**
     * Returns the appropriate adapter for reading {@code section} based on which
     * NBT keys are present. EID-specific keys indicate an extended-ID section.
     */
    public static BiomeAdapter detect(NBTTagCompound section) {
        if (section.hasKey("Biomes16v2")) {
            return EID;
        }

        return VANILLA;
    }

    /** Returns the singleton {@link VanillaSectionAdapter}. */
    public static BiomeAdapter vanilla() { return VANILLA; }

    /** Returns the singleton {@link EIDSectionAdapter}. */
    public static BiomeAdapter eid()     { return EID; }
}
