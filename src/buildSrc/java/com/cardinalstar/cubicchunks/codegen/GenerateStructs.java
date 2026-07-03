package com.cardinalstar.cubicchunks.codegen;

import java.io.IOException;

public class GenerateStructs {

    public static void main(String[] args) throws IOException {
        NOISE2D_UNIFORM.writeAllGL();
        ENHANCED_BLOCK_PICKER.writeAllGL();
        BIOME_DISTANCE.writeAllGL();
        BIOME_LOOKUP.writeAllGL();
        HEIGHTMAP.writeAllGL();
    }

    private static final Struct NOISE2D_UNIFORM = new Struct("com.cardinalstar.cubicchunks.api.worldgen.hwaccel", "Noise2DUniform")
        .addField(FieldType.i32, "offsetX")
        .addField(FieldType.i32, "offsetZ")
        .addField(FieldType.u32, "outputOffset");

    private static final Struct ENHANCED_BLOCK_PICKER = new Struct("com.cardinalstar.cubicchunks.worldgen.ccenhanced", "BlockPickerUniform")
        .addField(FieldType.i32, "cubeY")
        .addField(FieldType.u32, "heightmapOffset")
        .addField(FieldType.u32, "blockOffset");

    private static final Struct BIOME_DISTANCE = new Struct("com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome", "BiomeDistanceUniform")
        .addField(FieldType.u32, "temperatureOffset")
        .addField(FieldType.u32, "humidityOffset")
        .addField(FieldType.u32, "continentalnessOffset")
        .addField(FieldType.u32, "erosionOffset")
        .addField(FieldType.u32, "distanceOffset");

    private static final Struct BIOME_LOOKUP = new Struct("com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome", "BiomeLookupUniform")
        .addField(FieldType.u32, "distanceOffset")
        .addField(FieldType.u32, "closestOffset")
        .addField(FieldType.u32, "weightsOffset");

    private static final Struct HEIGHTMAP = new Struct("com.cardinalstar.cubicchunks.worldgen.ccenhanced.biome", "HeightMapUniform")
        .addField(FieldType.u32, "distancesOffset")
        .addField(FieldType.u32, "hvNoiseOffset")
        .addField(FieldType.u32, "heightMapOffset");
}
