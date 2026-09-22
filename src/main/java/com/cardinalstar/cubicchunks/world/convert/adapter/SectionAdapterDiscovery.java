package com.cardinalstar.cubicchunks.world.convert.adapter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Singleton registry for {@link SectionAdapter} instances.
 *
 * <p>{@link #detect} selects the correct adapter to use when reading a section
 * NBT compound by checking for EID-specific keys.
 */
public class SectionAdapterDiscovery {

    /**
     * All NBT keys within a section compound that any {@link SectionAdapter} reads or writes,
     * plus {@code "Y"}. Used by converters to identify unknown (mod-injected) keys that should
     * be preserved verbatim.
     */
    public static final Set<String> SECTION_MANAGED_KEYS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "Y",
        "Blocks", "Add", "BlocksB2Hi", "BlocksB3", "Block16", "Blocks16",
        "Data", "Data1High", "Data2", "Data16",
        "BlockLight", "SkyLight"
    )));

    private static final VanillaSectionAdapter VANILLA = new VanillaSectionAdapter();
    private static final EIDSectionAdapter EID = new EIDSectionAdapter();

    /**
     * Returns the appropriate adapter for reading {@code section} based on which
     * NBT keys are present. EID-specific keys indicate an extended-ID section.
     */
    public static SectionAdapter detect(NBTTagCompound section) {
        if (section.hasKey("BlocksB2Hi") || section.hasKey("BlocksB3")
                || section.hasKey("Data1High") || section.hasKey("Data2")
                || section.hasKey("Block16") || section.hasKey("Data16")) {
            return EID;
        }
        return VANILLA;
    }

    /** Returns the singleton {@link VanillaSectionAdapter}. */
    public static SectionAdapter vanilla() { return VANILLA; }

    /** Returns the singleton {@link EIDSectionAdapter}. */
    public static SectionAdapter eid()     { return EID; }
}
