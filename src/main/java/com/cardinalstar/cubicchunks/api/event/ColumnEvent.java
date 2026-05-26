package com.cardinalstar.cubicchunks.api.event;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.WorldEvent;

public class ColumnEvent extends WorldEvent {

    public final ChunkCoordIntPair pos;

    private ColumnEvent(World world, ChunkCoordIntPair pos) {
        super(world);
        this.pos = pos;
    }

    /// Invoked on the server thread, before the column is loaded.
    /// Allows the modification of the tag that will be loaded.
    public static class LoadNBT extends ColumnEvent {

        public NBTTagCompound tag;

        public LoadNBT(World world, ChunkCoordIntPair pos, NBTTagCompound tag) {
            super(world, pos);
            this.tag = tag;
        }
    }

    /// Invoked on the server thread, before the column is saved.
    /// Allows the modification of the tag that will be saved.
    public static class SaveNBT extends ColumnEvent {

        public final Chunk chunk;
        public NBTTagCompound tag;

        public SaveNBT(World world, Chunk chunk, NBTTagCompound tag) {
            super(world, chunk.getChunkCoordIntPair());
            this.chunk = chunk;
            this.tag = tag;
        }
    }
}
