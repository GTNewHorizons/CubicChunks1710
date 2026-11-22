package com.cardinalstar.cubicchunks.world.savedata;

import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.world.storage.StorageFormatFactory;
import com.cardinalstar.cubicchunks.server.chunkio.LegacyWorldConversion;

import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

public class WorldFormatSavedData extends WorldSavedData {

    private StorageFormatFactory format;
    /** The format name stored in the world, kept when no registered format has it. */
    private String storedName;

    public WorldFormatSavedData(String name) {
        super(name);
    }

    public StorageFormatFactory getFormat() {
        return format;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        storedName = tag.getString("format");
        // MapStorage only logs an exception thrown here and goes on with the data; an unknown format fails in get()
        try {
            format = StorageFormatFactory.REGISTRY.get(new UniqueIdentifier(storedName));
        } catch (RuntimeException e) {
            format = null;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setString("format", format.registryName.toString());
    }

    /** The format for a new world: {@code storageFormat} if it names a registered format, else the default. */
    private static StorageFormatFactory newWorldFormat() {
        String configured = CubicChunksConfig.storageFormat.trim();
        if (!configured.isEmpty()) {
            // Forge's UniqueIdentifier constructor needs exactly "mod:name"
            String[] parts = configured.split(":", -1);
            StorageFormatFactory format = parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()
                ? StorageFormatFactory.REGISTRY.get(new UniqueIdentifier(configured))
                : null;
            if (format != null) return format;
            CubicChunks.LOGGER.error(
                "storageFormat \"{}\" in cubicchunks.cfg is not a known storage format; using {}",
                configured,
                StorageFormatFactory.DEFAULT);
        }
        return StorageFormatFactory.REGISTRY.get(StorageFormatFactory.DEFAULT);
    }

    private static boolean hasCubeStorage(Path world) {
        return Files.isDirectory(world.resolve("region2d")) || Files.isDirectory(world.resolve("region3d"));
    }

    private static WorldFormatSavedData createMarker(World world, StorageFormatFactory format) {
        WorldFormatSavedData data = new WorldFormatSavedData("cubicchunks.world_format");
        data.format = format;
        data.markDirty();
        world.mapStorage.setData(data.mapName, data);
        world.mapStorage.saveAllData();
        return data;
    }

    public static WorldFormatSavedData get(World world) {
        WorldFormatSavedData data = (WorldFormatSavedData) world.mapStorage
            .loadData(WorldFormatSavedData.class, "cubicchunks.world_format");
        Path root = world.getSaveHandler()
            .getWorldDirectory()
            .toPath();

        if (data != null && data.format == null && LegacyWorldConversion.handles(data.storedName)) {
            // only in the first releases with bundle storage, see LegacyWorldConversion
            LegacyWorldConversion.convert(root, data.storedName);
            data.format = StorageFormatFactory.REGISTRY.get(StorageFormatFactory.DEFAULT);
            data.markDirty();
            world.mapStorage.saveAllData();
        }

        // only in the first releases with bundle storage: a world from before the marker existed, or one that lost it
        if (data == null && LegacyWorldConversion.hasStorage(root)) {
            LegacyWorldConversion.convert(root, "no format marker");
            return createMarker(world, StorageFormatFactory.REGISTRY.get(StorageFormatFactory.DEFAULT));
        }

        if (data != null && data.format == null) {
            throw new IllegalStateException(
                "Could not load world: it is saved in the storage format " + data.storedName
                    + ", which this version of CubicChunks cannot read. Open it once with one of the first CubicChunks"
                    + " releases with bundle storage, which convert it, see docs/bundle-storage.md.");
        }

        if (data == null) {
            if (hasCubeStorage(root)) {
                throw new IllegalStateException(
                    "Could not load world: it holds cube storage but no format marker"
                        + " (data/cubicchunks.world_format.dat), so CubicChunks cannot tell whether it can read it.");
            }
            data = createMarker(world, newWorldFormat());
        }

        return data;
    }
}
