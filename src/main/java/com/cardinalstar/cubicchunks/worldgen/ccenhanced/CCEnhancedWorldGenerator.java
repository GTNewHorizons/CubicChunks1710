package com.cardinalstar.cubicchunks.worldgen.ccenhanced;

import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL43.glDispatchCompute;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.block.Block;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.jetbrains.annotations.NotNull;

import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.api.worldgen.GenerationResult;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.AcceleratableWorldGenerator;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.ComputePlan;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelContext;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelExecutor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelSubmission;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelSubmissionResult;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.KernelSubmissionToken;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.Noise2DKernelExecutor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferAllocator;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDataType;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.BufferDescriptor;
import com.cardinalstar.cubicchunks.api.worldgen.hwaccel.buffer.GPUBuffer;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal.Server;
import com.cardinalstar.cubicchunks.mixin.ext.EBSIDAccessor;
import com.cardinalstar.cubicchunks.server.CubeProviderServer;
import com.cardinalstar.cubicchunks.server.chunkio.ICubeLoader;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.Mods;
import com.cardinalstar.cubicchunks.world.core.IColumnInternal;
import com.cardinalstar.cubicchunks.world.cube.Cube;
import com.cardinalstar.cubicchunks.world.worldgen.noise.OctavesSampler;
import com.cardinalstar.cubicchunks.world.worldgen.noise.ScaledSampler;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome.BiomeDistanceKernelExecutor;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome.BiomeLookupKernelExecutor;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome.BiomeLookupResult;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome.CCBiomeGenBase;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome.CCBiomeRegistry;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome.HeightMapKernelExecutor;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.climate.ClimateSystem;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.surface.CCSurfacePainter;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.terrain.CCBiomeCache;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.terrain.CCTerrainGenerator;
import com.cardinalstar.cubicchunks.worldgen.ccenhanced.terrain.ColumnContext;
import com.falsepattern.endlessids.mixin.helpers.ChunkBiomeHook;
import com.google.common.collect.ImmutableMap;
import com.gtnewhorizon.gtnhlib.hash.Fnv1a64;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

@ParametersAreNonnullByDefault
public class CCEnhancedWorldGenerator implements IWorldGenerator, AcceleratableWorldGenerator {

    /** ColumnContext LRU cache capacity: enough to cover a generous chunk-loading radius. */
    private static final int CONTEXT_CACHE_CAPACITY = 256;

    private final World world;
    private final ClimateSystem climateSystem;
    private final CCBiomeCache biomeCache;
    private final CCTerrainGenerator terrainGen;
    private final CCSurfacePainter surfacePainter;

    // LRU cache: column position key → ColumnContext computed during provideColumn,
    // read back during provideCube calls for the same column.
    private final LinkedHashMap<Long, ColumnContext> contextCache;

    public CCEnhancedWorldGenerator(World world) {
        this.world = world;
        long seed = world.getSeed();
        this.climateSystem = new ClimateSystem(seed);
        this.biomeCache = new CCBiomeCache(climateSystem);
        this.terrainGen = new CCTerrainGenerator(seed);
        this.surfacePainter = new CCSurfacePainter();
        this.contextCache = new LinkedHashMap<>(CONTEXT_CACHE_CAPACITY, 0.75f, true) {

            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, ColumnContext> eldest) {
                return size() > CONTEXT_CACHE_CAPACITY;
            }
        };
    }

    private @NotNull ICubeLoader getCubeLoader() {
        return getCubeProvider().getCubeLoader();
    }

    private CubeProviderServer getCubeProvider() {
        return ((Server) this.world).getCubeCache();
    }

    // -------------------------------------------------------------------------
    // Column
    // -------------------------------------------------------------------------

    @Override
    public GenerationResult<Chunk> provideColumn(World world, int columnX, int columnZ) {
        Chunk chunk = new Chunk(world, columnX, columnZ);

        ColumnContext ctx = getOrComputeContext(chunk, columnX, columnZ);

        BiomeLookupResult[] grid = biomeCache.getGrid(columnX, columnZ);
//        fillBiomeArray(chunk, grid);

        ExtendedBlockStorage[] ebsArr = new ExtendedBlockStorage[16];
        for (int cubeY = 0; cubeY < 16; cubeY++) {
            ebsArr[cubeY] = terrainGen.buildEbs(world, ctx, cubeY);
        }

        surfacePainter.paint(world, ebsArr, ctx, grid);

        List<Cube> cubes = new ArrayList<>(16);
        for (int cubeY = 0; cubeY < 16; cubeY++) {
            cubes.add(new Cube(chunk, cubeY, ebsArr[cubeY]));
        }

        return new GenerationResult<>(chunk, null, cubes);
    }

    // -------------------------------------------------------------------------
    // Cube
    // -------------------------------------------------------------------------

    @Override
    public GenerationResult<Cube> provideCube(@Nullable Chunk chunk, int cubeX, int cubeY, int cubeZ) {
        List<Chunk> generatedColumns = new ArrayList<>();
        List<Cube> generatedCubes = new ArrayList<>();

        // Generate column + all vanilla-range cubes if chunk is missing or
        // the requested cube falls in the vanilla range.
        if ((cubeY >= 0 && cubeY < 16) || chunk == null) {
            if (chunk == null) {
                chunk = new Chunk(world, cubeX, cubeZ);
                ((IColumnInternal) chunk).setColumn(true);
                generatedColumns.add(chunk);
            }
            ColumnContext ctx = getOrComputeContext(chunk, cubeX, cubeZ);

            BiomeLookupResult[] grid = biomeCache.getGrid(cubeX, cubeZ);
//            fillBiomeArray(chunk, grid);

            ExtendedBlockStorage[] ebsArr = new ExtendedBlockStorage[16];
            for (int cy = 0; cy < 16; cy++) {
                ebsArr[cy] = terrainGen.buildEbs(world, ctx, cy);
            }

            surfacePainter.paint(world, ebsArr, ctx, grid);

            for (int cy = 0; cy < 16; cy++) {
                generatedCubes.add(new Cube(chunk, cy, ebsArr[cy]));
            }
        }

        // Cubes outside [0, 16) are generated individually using the same terrain rules.
        if (cubeY < 0 || cubeY >= 16) {
            ColumnContext ctx = getOrComputeContext(chunk, cubeX, cubeZ);
            generatedCubes.add(new Cube(chunk, cubeY, terrainGen.buildEbs(world, ctx, cubeY)));
        }

        // Extract the requested cube as the primary result.
        Cube primary = null;
        for (int i = 0; i < generatedCubes.size(); i++) {
            Cube c = generatedCubes.get(i);
            if (c.getY() == cubeY) {
                primary = c;
                generatedCubes.remove(i);
                break;
            }
        }

        return new GenerationResult<>(primary, generatedColumns, generatedCubes);
    }

    // -------------------------------------------------------------------------
    // Population
    // -------------------------------------------------------------------------

    @Override
    public void populate(Cube cube) {
        cube.markPopulated(Cube.POP_ALL);
    }

    // -------------------------------------------------------------------------
    // Structure stubs
    // -------------------------------------------------------------------------

    @Override
    public void recreateStructures(ICube cube) {}

    @Override
    public void recreateStructures(Chunk column) {}

    @Override
    public List<BiomeGenBase.SpawnListEntry> getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        return BiomeGenBase.plains.getSpawnableList(type);
    }

    @Override
    @Nullable
    public ChunkPosition getNearestStructure(String name, int x, int y, int z) {
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns the cached ColumnContext for this column, computing it on demand if absent. */
    private ColumnContext getOrComputeContext(Chunk chunk, int chunkX, int chunkZ) {
        long key = packKey(chunkX, chunkZ);
        ColumnContext ctx = contextCache.get(key);
        if (ctx == null) {
            ctx = terrainGen.computeColumnContext(chunk, chunkX, chunkZ, biomeCache);
            contextCache.put(key, ctx);
        }
        return ctx;
    }

    /**
     * Writes the primary biome ID for each block column into the chunk's biome array
     * (indexed as bz&lt;&lt;4|bx), so the game reports correct biome names and spawning.
     */
    private static void fillBiomeArray(Chunk chunk, BiomeLookupResult[] grid) {
        byte[] biomeArray = chunk.getBiomeArray();

        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                CCBiomeGenBase biome = grid[bz << 4 | bx].primary;
                biomeArray[bz << 4 | bx] = (byte) (biome.biomeID & 0xFF);
            }
        }
    }

    private static long packKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private boolean kernelsInitialized;
    private KernelExecutor<ChunkCoordIntPair> tempKernel;
    private KernelExecutor<ChunkCoordIntPair> humidityKernel;
    private KernelExecutor<ChunkCoordIntPair> contKernel;
    private KernelExecutor<ChunkCoordIntPair> erosionKernel;
    private BiomeDistanceKernelExecutor distancesKernel;
    private BiomeLookupKernelExecutor lookupKernel;
    private Noise2DKernelExecutor hvNoiseKernel;
    private HeightMapKernelExecutor heightMapKernel;
    private BlockGenKernelExecutor blockKernel;

    @Override
    public ComputePlan plan(
        @Nullable Chunk column, int columnX, int columnZ,
        IntArrayList cubeYLevels
    ) {
        if (!kernelsInitialized) {
            long worldSeed = world.getSeed();
            long hvSeed = Fnv1a64.hashStep(Fnv1a64.hashStep(Fnv1a64.initialState(), worldSeed), 100L);
            List<CCBiomeGenBase> biomes = CCBiomeRegistry.getBiomes();
            KernelContext.getScheduler().runAndWait(() -> {
                kernelsInitialized = true;

                tempKernel = climateSystem.createAxisKernel(ClimateSystem.TEMPERATURE);
                humidityKernel = climateSystem.createAxisKernel(ClimateSystem.HUMIDITY);
                contKernel = climateSystem.createAxisKernel(ClimateSystem.CONTINENTALNESS);
                erosionKernel = climateSystem.createAxisKernel(ClimateSystem.EROSION);

                distancesKernel = new BiomeDistanceKernelExecutor(biomes.toArray(new CCBiomeGenBase[0]));
                lookupKernel = new BiomeLookupKernelExecutor(biomes.size());

                hvNoiseKernel = new Noise2DKernelExecutor(
                    new ScaledSampler(new OctavesSampler(new Random(hvSeed), 4), 1.0 / 400.0));

                heightMapKernel = new HeightMapKernelExecutor(biomes);
                blockKernel = new BlockGenKernelExecutor();
            });
        }

        ComputePlan plan = new ComputePlan();

        // Steps 1–3: four per-axis climate kernels write into one shared buffer, then distances → lookup
        ChunkCoordIntPair chunkCoord = new ChunkCoordIntPair(columnX, columnZ);

        var temperature = plan.submit(tempKernel, chunkCoord).get("noise");
        var humidity = plan.submit(humidityKernel, chunkCoord).get("noise");
        var continentalness = plan.submit(contKernel, chunkCoord).get("noise");
        var erosion = plan.submit(erosionKernel, chunkCoord).get("noise");

        var distances = plan.submit(distancesKernel, null, ImmutableMap.of("temperature", temperature, "humidity", humidity, "continentalness", continentalness, "erosion", erosion));

        var lookup = plan.submit(lookupKernel, null, ImmutableMap.of("distances", distances.get("distances")));

        // Step 4: HV noise (independent of climate pipeline)
        var hvNoise = plan.submit(hvNoiseKernel, chunkCoord);

        // Step 5: height map — blends all biomes via Gaussian falloff over raw distances,
        // avoiding top-N membership discontinuities at biome boundaries.
        var heightData = plan.submit(heightMapKernel, null, ImmutableMap.of(
            "distances", distances.get("distances"),
            "hvNoise", hvNoise.get("noise")
        ));

        IntOpenHashSet cubesToGenerate = new IntOpenHashSet(cubeYLevels);

        if (column == null) {
            column = new Chunk(world, columnX, columnZ);

            Chunk column2 = column;

            plan.terminal(ImmutableMap.of(), inputs -> this.processColumn(inputs, column2));

            // Generate all terrain-range cubes atomically to prevent sync-path side-effect conflicts.
            // provideCube generates y∈[0,15] as side effects for any request in that range.
            for (int y = 0; y < 16; y++) {
                cubesToGenerate.add(y);
            }
        }

        for (Integer cubeY : cubesToGenerate) {
            var blockData = plan.submit(blockKernel, new CubePos(columnX, cubeY, columnZ), ImmutableMap.of("heightMap", heightData.get("heightMap")));

            Cube cube = new Cube(column, cubeY);

            plan.terminal(blockData, inputs -> this.processCube(inputs, cube));
        }

        return plan;
    }

    private void processColumn(Map<String, ByteBuffer> inputs, Chunk column) {
//        var biomes = inputs.get("closestBiomes").asIntBuffer();
//
//        for (int z = 0; z < 16; z++) {
//            for (int x = 0; x < 16; x++) {
//                int biomeIndex = biomes.get(((z << 4) | x) * 4);
//
//                CCBiomeGenBase biome = CCBiomeRegistry.getBiome(biomeIndex);
//
//                putBiome(column, x, z, biome);
//            }
//        }

        getCubeLoader().addColumn(column);
    }

    private static void putBiome(Chunk column, int x, int z, CCBiomeGenBase biome) {
        if (Mods.EndlessIDs.isModLoaded()) {
            var biomes = ((ChunkBiomeHook) column).getBiomeShortArray();

            biomes[(z << 4) | x] = (short) biome.biomeID;
        } else {
            var biomes = column.getBiomeArray();

            biomes[(z << 4) | x] = (byte) biome.biomeID;
        }
    }

    private void processCube(Map<String, ByteBuffer> inputs, Cube cube) {
        var rawBlockData = inputs.get("blocks").asIntBuffer();

        ExtendedBlockStorage ebs = cube.getOrCreateStorage();

        EBSIDAccessor ids = (EBSIDAccessor) ebs;

        int stone = Block.getIdFromBlock(Blocks.stone);
        int dirt = Block.getIdFromBlock(Blocks.dirt);
        int grass = Block.getIdFromBlock(Blocks.grass);
        int log = Block.getIdFromBlock(Blocks.log);

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int block = rawBlockData.get((z << 8) | (y << 4) | x);

                    if (block == 0) continue;

                    ids.setBlockID(x, y, z, switch (block) {
                        case 1 -> stone;
                        case 2 -> dirt;
                        case 3 -> grass;
                        default -> log;
                    }, block == 3);
                }
            }
        }

        getCubeLoader().addCube(cube);
    }

    private static class BlockGenKernelExecutor implements KernelExecutor<CubePos> {

        private final int program;

        public BlockGenKernelExecutor() {
            String code = """
            #version 430 core

            layout(local_size_x = 16, local_size_y = 16, local_size_z = 1) in;

            $uniform

            layout(std430, binding = 0) buffer Arena { uint arena[]; };
            layout(std430, binding = 1) readonly buffer Uniforms { BlockPickerUniform uniforms[]; };

            void main() {
                uint id = gl_WorkGroupID.x;

                uint lx = gl_LocalInvocationID.x;
                uint ly = gl_LocalInvocationID.y;
                uint lz = gl_LocalInvocationID.z + gl_WorkGroupID.z;

                int cubeY = uniforms[id].cubeY;
                uint heightmapOffset = uniforms[id].heightmapOffset;
                uint blockOffset = uniforms[id].blockOffset;

                int gy = int(ly) + (cubeY << 4);

                int topBlock = int(uintBitsToFloat(arena[heightmapOffset + ((lz << 4) | lx)]));

                uint block = 0; // air

                block = gy < topBlock ? 2 : block; // dirt
                block = gy < topBlock - 3 ? 1 : block; // stone overrides dirt for deep blocks
                block = gy == topBlock ? 3 : block; // grass

                arena[blockOffset + ((lz << 8) | (ly << 4) | lx)] = block;
            }
            """
                .replace("$uniform", BlockPickerUniformGLStruct.SOURCE);

            this.program = KernelExecutor.createProgram(code);
        }

        @Override
        public Map<String, BufferDescriptor> getOutputs(
            ComputePlan plan,
            KernelSubmissionToken submission,
            CubePos cubePos,
            Map<String, BufferDescriptor> inputs
        ) {
            var heightMap = inputs.get("heightMap");
            Objects.requireNonNull(heightMap, "expected heightMap input buffer");
            heightMap.assertLayout(BufferDataType.f32, 16, 16);

            return ImmutableMap.of("blocks", plan.describeBuffer(submission, BufferDataType.u32, 16, 16, 16));
        }

        @Override
        public KernelSubmissionResult[] submit(BufferAllocator alloc, KernelSubmission<CubePos>[] submissions) {
            glUseProgram(this.program);

            BlockPickerUniformPrimitiveBuffer uniforms = new BlockPickerUniformPrimitiveBuffer(submissions.length);

            KernelSubmissionResult[] results = new KernelSubmissionResult[submissions.length];

            uniforms.forEachFast((i, view) -> {
                var key = submissions[i].key();

                GPUBuffer input = submissions[i].inputs().get("heightMap");

                GPUBuffer output = alloc.alloc(BufferDataType.u32, 16, 16, 16);
                results[i] = new KernelSubmissionResult(ImmutableMap.of("blocks", output));

                view.setCubeY(key.getY());
                view.setHeightmapOffset(input.getBufferOffset() / 4);
                view.setBlockOffset(output.getBufferOffset() / 4);

                return true;
            });

            GPUBuffer uniformGPU = alloc.uniform(uniforms);

            alloc.bindSSBO(0);
            uniformGPU.bind(1);

            glDispatchCompute(submissions.length, 1, 16);

            alloc.unbindSSBO(0);
            uniformGPU.unbind(1);

            glUseProgram(0);

            return results;
        }
    }
}
