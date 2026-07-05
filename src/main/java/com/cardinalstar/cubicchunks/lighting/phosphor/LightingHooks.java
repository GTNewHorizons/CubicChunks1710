package com.cardinalstar.cubicchunks.lighting.phosphor;

import static com.cardinalstar.cubicchunks.util.Coords.blockToCube;
import static com.cardinalstar.cubicchunks.util.Coords.blockToLocal;
import static com.cardinalstar.cubicchunks.util.Coords.cubeToMinBlock;
import static com.cardinalstar.cubicchunks.util.Coords.localToBlock;

import java.util.Arrays;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import com.cardinalstar.cubicchunks.api.IColumn;
import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.lighting.LightingManager;
import com.cardinalstar.cubicchunks.util.DirectionUtils;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.cube.ICubeProvider;
import com.gtnewhorizon.gtnhlib.client.renderer.cel.model.quad.properties.ModelQuadFacing.Axis;

public class LightingHooks {

    private static final EnumSkyBlock[] ENUM_SKY_BLOCK_VALUES = EnumSkyBlock.values();

    public static void relightSkylightColumn(final World world, final Chunk chunk, final int localX, final int localZ,
        final int height1, final int height2) {
        final int yMin = Math.min(height1, height2);
        final int yMax = Math.max(height1, height2);

        final int xBase = localToBlock(chunk.xPosition, localX);
        final int zBase = localToBlock(chunk.zPosition, localZ);

        scheduleRelightChecksForColumn(world, chunk, EnumSkyBlock.Sky, xBase, zBase, yMin, yMax);
    }

    public static void scheduleRelightChecksForArea(final World world, final EnumSkyBlock lightType, final int xMin,
        final int yMin, final int zMin, final int xMax, final int yMax, final int zMax) {
        // TODO: cache chunk
        for (int x = xMin; x <= xMax; ++x) {
            for (int z = zMin; z <= zMax; ++z) {
                scheduleRelightChecksForColumn(world, lightType, x, z, yMin, yMax);
            }
        }
    }

    private static void scheduleRelightChecksForColumn(final World world, final EnumSkyBlock lightType, final int x,
        final int z, final int yMin, final int yMax) {

        scheduleRelightChecksForColumn(
            world,
            world.getChunkFromBlockCoords(x, z),
            lightType,
            x,
            z,
            yMin,
            yMax);
    }

    private static void scheduleRelightChecksForColumn(final World world, final Chunk chunk,
        final EnumSkyBlock lightType, final int x, final int z, final int yMin, final int yMax) {

        final int yMinCube = blockToCube(yMin);
        final int yMaxCube = blockToCube(yMax);

        Iterable<? extends ICube> cubes = ((IColumn) chunk).getLoadedCubes(yMaxCube, yMinCube);

        int posX;
        int posY;
        int posZ;

        for (ICube cube : cubes) {
            int cubeY = cube.getY();
            int baseY = cubeToMinBlock(cubeY);
            int minLocalY = 0, maxLocalY = 15;
            if (cubeY == yMinCube) {
                minLocalY = blockToLocal(yMin);
            }
            if (cubeY == yMaxCube) {
                maxLocalY = blockToLocal(yMax);
            }
            for (int localY = minLocalY; localY <= maxLocalY; localY++) {
                posX = x;
                posY = baseY + localY;
                posZ = z;
                world.updateLightByType(lightType, posX, posY, posZ);
            }
        }
    }

    public enum EnumBoundaryFacing {
        IN,
        OUT
    }

    public static void flagSecBoundaryForUpdate(final ICube cube, final int x, final int y, final int z,
        final EnumSkyBlock lightType, final EnumFacing dir, final EnumBoundaryFacing boundaryFacing) {

        flagCubeBoundaryForUpdate(cube, lightType, dir, getQuadrantId(dir, x, y, z), boundaryFacing);
    }

    public static void flagCubeBoundaryForUpdate(final ICube cube, final EnumSkyBlock lightType, final EnumFacing dir,
        final int quadrantId, final EnumBoundaryFacing boundaryFacing) {

        LightingManager.CubeLightData info = (LightingManager.CubeLightData) ((Cube) cube).getCubeLightData();
        final long flagBit = 1L << getFlagIndex(dir, quadrantId, boundaryFacing);
        if (lightType == EnumSkyBlock.Sky) {
            info.neighborLightChecksSky |= flagBit;
        } else {
            assert lightType == EnumSkyBlock.Block;
            info.neighborLightChecksBlock |= flagBit;
        }
        ((Cube) cube).markDirty();
    }

    public static int getFlagIndex(final int xOffset, final int yOffset, final int zOffset, final int quadrantId,
        final EnumBoundaryFacing boundaryFacing) {
        // only one of x/y/zOffset at a time is non-zero
        // zOffset=+/-1 -> 0
        // xOffset=+/-1 -> 1
        // yOffset=+/-1 -> 2
        final int axisId = (xOffset & 1) | ((yOffset & 1) << 1);
        // extract the sign bit of each of them and OR them, only one is non-zero so it will extract the sign of current
        // axis
        final int axisDirection = (xOffset >>> 31) | (yOffset >>> 31) | (zOffset >>> 31);
        final int faceId = axisId << 1 | axisDirection;
        return (faceId << 3) | (quadrantId << 1) | boundaryFacing.ordinal();
    }

    // TODO WATCH
    public static int getFlagIndex(final EnumFacing dir, final int quadrantId,
        final EnumBoundaryFacing boundaryFacing) {
        return getFlagIndex(
            dir.getFrontOffsetX(),
            dir.getFrontOffsetY(),
            dir.getFrontOffsetZ(),
            quadrantId,
            boundaryFacing);
    }

    /*
     * Extract the quadrant id. Assuming dir axis goes in/out of the screen:
     * +----+----+
     * ^ | 1 | 3 |
     * | +----O----+
     * x1 | 0 | 2 |
     * +----+----+
     * x2 -->
     */
    private static int getQuadrantId(final EnumFacing dir, final int x, final int y, final int z) {
        int x1, x2;
        Axis axis = DirectionUtils.fromFacing(dir);
        // get coordinates perpendicular to the axis of direction
        if (axis == Axis.X) {
            // noinspection SuspiciousNameCombination
            x1 = y;
            x2 = z;
        } else if (axis == Axis.Y) {
            x1 = x;
            x2 = z;
        } else {
            assert axis == Axis.Z;
            x1 = x;
            // noinspection SuspiciousNameCombination
            x2 = y;
        }
        // extract most significant bit -> lower or upper have of the axis
        x1 = blockToLocal(x1) >> 3;
        x2 = blockToLocal(x2) >> 3;

        return x1 | (x2 << 1);
    }

    public static void scheduleRelightChecksForCubeBoundaries(final World world, final ICube cube) {
        for (final EnumFacing dir : Arrays.asList(EnumFacing.values())) {
            final int xOffset = dir.getFrontOffsetX();
            final int yOffset = dir.getFrontOffsetY();
            final int zOffset = dir.getFrontOffsetZ();

            final ICube nCube = ((ICubeProvider) world.getChunkProvider())
                .getLoadedCube(cube.getX() + xOffset, cube.getY() + yOffset, cube.getZ() + zOffset);

            if (nCube == null) {
                continue;
            }

            for (final EnumSkyBlock lightType : ENUM_SKY_BLOCK_VALUES) {
                for (int quadrantId = 0; quadrantId < 4; quadrantId++) {
                    // Merge flags upon loading of a chunk. This ensures that all flags are always already on the IN
                    // boundary below
                    mergeFlags(lightType, cube, nCube, dir, quadrantId);
                    mergeFlags(lightType, nCube, cube, DirectionUtils.getOpposite(dir), quadrantId);

                    // Check everything that might have been canceled due to this chunk not being loaded.
                    // Also, pass in chunk if already known
                    // The boundary to the neighbor chunk (both ways)
                    scheduleRelightChecksForBoundary(
                        world,
                        cube,
                        nCube,
                        lightType,
                        xOffset,
                        yOffset,
                        zOffset,
                        quadrantId);
                    scheduleRelightChecksForBoundary(
                        world,
                        nCube,
                        cube,
                        lightType,
                        -xOffset,
                        -yOffset,
                        -zOffset,
                        quadrantId);
                }
            }
        }
    }

    private static void mergeFlags(final EnumSkyBlock lightType, final ICube inCube, final ICube outCube,
        final EnumFacing dir, final int quadrantId) {
        LightingManager.CubeLightData outCubeLightingData = (LightingManager.CubeLightData) ((Cube) outCube)
            .getCubeLightData();

        LightingManager.CubeLightData inCubeLightingData = (LightingManager.CubeLightData) ((Cube) inCube)
            .getCubeLightData();

        final int inIndex = getFlagIndex(dir, quadrantId, EnumBoundaryFacing.IN);
        final int outIndex = getFlagIndex(DirectionUtils.getOpposite(dir), quadrantId, EnumBoundaryFacing.OUT);

        if (lightType == EnumSkyBlock.Sky) {
            inCubeLightingData.neighborLightChecksSky |= ((outCubeLightingData.neighborLightChecksSky >> outIndex) & 1)
                << inIndex;
        } else {
            inCubeLightingData.neighborLightChecksBlock |= ((outCubeLightingData.neighborLightChecksBlock >> outIndex)
                & 1) << inIndex;
        }
        // no need to call Chunk.setModified() since checks are not deleted from outChunk
    }

    private static void scheduleRelightChecksForBoundary(final World world, final ICube cube, @Nullable ICube nCube,
        final EnumSkyBlock lightType, final int xOffset, final int yOffset, final int zOffset, final int quadrantId) {
        LightingManager.CubeLightData cubeInfo = (LightingManager.CubeLightData) ((Cube) cube).getCubeLightData();

        // OUT checks from neighbor are already merged
        final int flagIndex = getFlagIndex(xOffset, yOffset, zOffset, quadrantId, EnumBoundaryFacing.IN);

        final int flag = (int) (((lightType == EnumSkyBlock.Sky ? cubeInfo.neighborLightChecksSky
            : cubeInfo.neighborLightChecksBlock) >> flagIndex) & 1);

        if (flag == 0) {
            return;
        }

        if (nCube == null) {
            nCube = ((ICubeProvider) world.getChunkProvider())
                .getLoadedCube(cube.getX() + xOffset, cube.getY() + yOffset, cube.getZ() + zOffset);

            if (nCube == null) {
                return;
            }
        }

        final int reverseIndex = getFlagIndex(-xOffset, -yOffset, -zOffset, quadrantId, EnumBoundaryFacing.OUT);

        if (lightType == EnumSkyBlock.Sky) {
            cubeInfo.neighborLightChecksSky &= ~(1L << flagIndex);
        } else {
            cubeInfo.neighborLightChecksBlock &= ~(1L << flagIndex);
        }

        LightingManager.CubeLightData nCubeInfo = (LightingManager.CubeLightData) ((Cube) nCube).getCubeLightData();

        // Clear only now that it's clear that the checks are processed
        if (lightType == EnumSkyBlock.Sky) {
            nCubeInfo.neighborLightChecksSky &= ~(1L << reverseIndex);
        } else {
            nCubeInfo.neighborLightChecksBlock &= ~(1L << reverseIndex);
        }

        ((Cube) cube).markDirty();
        ((Cube) nCube).markDirty();

        int x1MinLocal = (quadrantId & 1) << 3;
        int x2MinLocal = (quadrantId & 2) << 2;
        int x1MaxLocal = x1MinLocal + 7;
        int x2MaxLocal = x2MinLocal + 7;

        int xMin = cube.getCoords()
            .getMinBlockX();
        int yMin = cube.getCoords()
            .getMinBlockY();
        int zMin = cube.getCoords()
            .getMinBlockZ();

        xMin += cubeToMinBlock(xOffset);
        yMin += cubeToMinBlock(yOffset);
        zMin += cubeToMinBlock(zOffset);

        int xMax = xMin;
        int yMax = yMin;
        int zMax = zMin;

        if (xOffset != 0) {
            // noinspection SuspiciousNameCombination
            yMin += x1MinLocal;
            // noinspection SuspiciousNameCombination
            yMax += x1MaxLocal;
            zMin += x2MinLocal;
            zMax += x2MaxLocal;
        } else if (yOffset != 0) {
            xMin += x1MinLocal;
            xMax += x1MaxLocal;
            zMin += x2MinLocal;
            zMax += x2MaxLocal;
        } else {
            assert zOffset != 0;
            xMin += x1MinLocal;
            xMax += x1MaxLocal;
            // noinspection SuspiciousNameCombination
            yMin += x2MinLocal;
            // noinspection SuspiciousNameCombination
            yMax += x2MaxLocal;
        }

        scheduleRelightChecksForArea(world, lightType, xMin, yMin, zMin, xMax, yMax, zMax);
    }

    public static final String neighborLightChecksSkyKey = "NeighborLightChecksSky";
    public static final String neighborLightChecksBlockKey = "NeighborLightChecksBlock";

    public static void writeNeighborLightChecksToNBT(final ICube cube, final NBTTagCompound nbt) {
        LightingManager.CubeLightData cubeLightUpdateInfo = (LightingManager.CubeLightData) ((Cube) cube)
            .getCubeLightData();
        nbt.setLong(neighborLightChecksSkyKey, cubeLightUpdateInfo.neighborLightChecksSky);
        nbt.setLong(neighborLightChecksBlockKey, cubeLightUpdateInfo.neighborLightChecksBlock);
    }

    public static void readNeighborLightChecksFromNBT(final ICube cube, final NBTTagCompound nbt) {
        LightingManager.CubeLightData cubeLightUpdateInfo = (LightingManager.CubeLightData) ((Cube) cube)
            .getCubeLightData();
        cubeLightUpdateInfo.neighborLightChecksSky = nbt.getLong(neighborLightChecksSkyKey);
        cubeLightUpdateInfo.neighborLightChecksBlock = nbt.getLong(neighborLightChecksBlockKey);
    }

    public static void initSkylightForSection(final World world, final Chunk chunk,
        final ExtendedBlockStorage section) {
        if (!world.provider.hasNoSky) {
            for (int x = 0; x < 16; ++x) {
                for (int z = 0; z < 16; ++z) {
                    if (((IColumnInternal) chunk).getTopYWithStaging(x, z) < section.getYLocation()) {
                        for (int y = 0; y < 16; ++y) {
                            section.setExtSkylightValue(x, y, z, EnumSkyBlock.Sky.defaultLightValue);
                        }
                    }
                }
            }
        }
    }
}
