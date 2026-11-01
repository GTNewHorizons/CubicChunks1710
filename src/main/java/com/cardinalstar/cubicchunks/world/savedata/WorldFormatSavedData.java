package com.cardinalstar.cubicchunks.world.savedata;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.world.storage.StorageFormatFactory;

import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

public class WorldFormatSavedData extends WorldSavedData {

    private StorageFormatFactory format;

    public WorldFormatSavedData(String name) {
        super(name);
    }

    public StorageFormatFactory getFormat() {
        return format;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        format = StorageFormatFactory.REGISTRY.get(new UniqueIdentifier(tag.getString("format")));

        if (format == null) {
            throw new IllegalStateException(
                "Could not load world: save format was not registered: " + tag.getString("format"));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setString("format", format.registryName.toString());
    }

    /**
     * Existing anvil3d and compact-empty worlds are converted to bundle storage unless {@code storageFormat} names
     * another format: an explicit setting, even one that is not a known format, is never overridden.
     */
    static boolean convertsExistingWorlds() {
        String configured = CubicChunksConfig.storageFormat.trim();
        if (configured.isEmpty() || configured.equals(StorageFormatFactory.BUNDLES.toString())) return true;
        // Forge's UniqueIdentifier constructor needs exactly "mod:name"
        String[] parts = configured.split(":", -1);
        if (parts.length != 2 || parts[0].isEmpty()
            || parts[1].isEmpty()
            || StorageFormatFactory.REGISTRY.get(new UniqueIdentifier(configured)) == null) {
            CubicChunks.LOGGER.error(
                "storageFormat \"{}\" in cubicchunks.cfg is not a known storage format; existing worlds are not converted",
                configured);
        }
        return false;
    }

    public static WorldFormatSavedData get(World world) {
        WorldFormatSavedData data = (WorldFormatSavedData) world.mapStorage
            .loadData(WorldFormatSavedData.class, "cubicchunks.world_format");

        if (data != null && convertsExistingWorlds()
            && (data.format.getRegistryName()
                .equals(StorageFormatFactory.ANVIL3D)
                || data.format.getRegistryName()
                    .equals(StorageFormatFactory.COMPACT_EMPTY))) {
            // Existing worlds are converted: bundle storage reads their records and rewrites each bundle when one of
            // its cubes is saved. Older CubicChunks builds cannot read bundle records, so the marker changes first.
            CubicChunks.LOGGER.warn(
                "Converting cube storage of this world from {} to {}. Older CubicChunks versions will not be able to"
                    + " open it afterwards.",
                data.format.getRegistryName(),
                StorageFormatFactory.BUNDLES);
            data.format = StorageFormatFactory.REGISTRY.get(StorageFormatFactory.BUNDLES);
            data.markDirty();
            world.mapStorage.saveAllData();
        }

        if (data == null) {
            data = new WorldFormatSavedData("cubicchunks.world_format");

            UniqueIdentifier id;

            if (CubicChunksConfig.storageFormat.isEmpty()) {
                id = StorageFormatFactory.DEFAULT;
            } else {
                id = new UniqueIdentifier(CubicChunksConfig.storageFormat);
            }

            data.format = StorageFormatFactory.REGISTRY.get(id);

            data.markDirty();
            world.mapStorage.setData(data.mapName, data);
            world.mapStorage.saveAllData();
        }

        return data;
    }
}
