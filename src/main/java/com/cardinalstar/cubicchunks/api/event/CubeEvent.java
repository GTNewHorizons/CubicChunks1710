/*
 * This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 * Copyright (c) 2015-2021 OpenCubicChunks
 * Copyright (c) 2015-2021 contributors
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cardinalstar.cubicchunks.api.event;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;

import com.cardinalstar.cubicchunks.server.CubicPlayerManager;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.ICubeProvider;
import lombok.Getter;

public class CubeEvent extends WorldEvent {

    public final CubePos pos;

    protected CubeEvent(World world, CubePos pos) {
        super(world);
        this.pos = pos;
    }

    /// Invoked immediately after a cube is inserted into the world, regardless of the cause.
    public static class Load extends CubeEvent {

        public final Cube cube;

        public Load(World world, Cube cube) {
            super(world, cube.getCoords());
            this.cube = cube;
        }
    }

    /// Invoked immediately before a cube is removed from the world.
    public static class Unload extends CubeEvent {

        public final Cube cube;

        public Unload(World world, Cube cube) {
            super(world, cube.getCoords());
            this.cube = cube;
        }
    }

    /// Invoked on the server thread, before the cube is loaded.
    /// Allows the modification of the tag that will be loaded.
    public static class LoadNBT extends CubeEvent {

        public final NBTTagCompound tag;

        public LoadNBT(World world, CubePos pos, NBTTagCompound tag) {
            super(world, pos);
            this.tag = tag;
        }
    }

    /// Invoked on the server thread, before the cube is saved.
    /// Allows the modification of the tag that will be saved.
    public static class SaveNBT extends CubeEvent {

        public final Cube cube;
        public final NBTTagCompound tag;

        public SaveNBT(World world, Cube cube, NBTTagCompound tag) {
            super(world, cube.getCoords());
            this.cube = cube;
            this.tag = tag;
        }
    }

    /// Invoked on the server thread, when a player starts watching a cube.
    public static class Watch extends CubeEvent {

        @Getter
        public final Cube cube;
        @Getter
        public final EntityPlayerMP player;

        public Watch(World world, Cube cube, EntityPlayerMP player) {
            super(world, cube.getCoords());
            this.cube = cube;
            this.player = player;
        }

        public ICubicWorld getWorld() {
            return (ICubicWorld) player.worldObj;
        }

        public CubicPlayerManager getPlayerManager() {
            return (CubicPlayerManager) ((WorldServer) getWorld()).getPlayerManager();
        }

        public ICubeProvider getCubeProvider() {
            return getWorld().getCubeCache();
        }
    }

    /// Invoked on the server thread, when a player stops watching a cube.
    public static class UnWatch extends CubeEvent {

        @Getter
        public final EntityPlayerMP player;

        public UnWatch(World world, CubePos pos, EntityPlayerMP player) {
            super(world, pos);
            this.player = player;
        }

        public ICubicWorld getWorld() {
            return (ICubicWorld) player.worldObj;
        }

        public CubicPlayerManager getPlayerManager() {
            return (CubicPlayerManager) ((WorldServer) getWorld()).getPlayerManager();
        }

        public ICubeProvider getCubeProvider() {
            return getWorld().getCubeCache();
        }
    }
}
