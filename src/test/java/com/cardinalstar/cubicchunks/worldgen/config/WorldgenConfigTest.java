package com.cardinalstar.cubicchunks.worldgen.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

import com.cardinalstar.cubicchunks.api.IntRange;

class WorldgenConfigTest {

    private static final WGConfigItem<IntRange> RANGE = WGConfigItem.of(
        new ResourceLocation("cctest", "range"),
        new IntRange(0, 256),
        WGConfigItemTypes.intRange(new IntRange(-1000, 1000), true, 1));

    private static final WGConfigItem<IntRange> ALIGNED = WGConfigItem.of(
        new ResourceLocation("cctest", "aligned"),
        new IntRange(0, 256),
        WGConfigItemTypes.intRange(new IntRange(-1000, 1000), true, 16));

    private static final WGConfigItem<BlockRef> BLOCK = WGConfigItem.of(
        new ResourceLocation("cctest", "nested/block"),
        new BlockRef("minecraft:stone", 0),
        WGConfigItemTypes.block());

    private static final WGConfigItem<BlockRef> FILLER = WGConfigItem
        .of(new ResourceLocation("cctest", "filler"), null, WGConfigItemTypes.blockOrAuto());

    private static final List<Stratum> DEFAULT_STRATA = Collections
        .singletonList(new Stratum(new BlockRef("minecraft:stone", 0), -50, -25));

    private static final WGConfigItem<List<Stratum>> STRATA = WGConfigItem
        .of(new ResourceLocation("cctest", "strata"), DEFAULT_STRATA, WGConfigItemTypes.strata());

    private static final WGConfigItem<String[]> LIST = WGConfigItem
        .of(new ResourceLocation("cctest", "list"), new String[] { "x" }, WGConfigItemTypes.stringList());

    private static final List<WGConfigItem<?>> ITEMS = Arrays.asList(RANGE, ALIGNED, BLOCK, FILLER, STRATA, LIST);
    private static final WGConfigItemSource SOURCE = dimensionId -> ITEMS;

    @TempDir
    File worldDir;

    @Test
    void missingFileLoadsEmpty() throws ConfigurateException {
        WorldgenConfig config = WorldgenConfig.load(worldDir, SOURCE);
        assertTrue(
            config.getDimensionIds()
                .isEmpty());
        assertNull(config.get(0));
    }

    @Test
    void roundTrip() throws ConfigurateException {
        WorldgenConfig config = new WorldgenConfig(SOURCE);
        WGDimensionConfig overworld = config.getOrCreate(0);
        overworld.set(RANGE, new IntRange(-64, 1000));
        overworld.set(BLOCK, new BlockRef("minecraft:dirt", 1));
        config.getOrCreate(-1)
            .set(LIST, new String[] { "a", "b" });
        config.save(worldDir);

        WorldgenConfig loaded = WorldgenConfig.load(worldDir, SOURCE);
        assertEquals(
            new IntRange(-64, 1000),
            loaded.get(0)
                .get(RANGE));
        assertEquals(
            new BlockRef("minecraft:dirt", 1),
            loaded.get(0)
                .get(BLOCK));
        assertArrayEquals(
            new String[] { "x" },
            loaded.get(0)
                .get(LIST));
        assertArrayEquals(
            new String[] { "a", "b" },
            loaded.get(-1)
                .get(LIST));
        assertEquals(
            new IntRange(0, 256),
            loaded.get(-1)
                .get(RANGE));
    }

    @Test
    void savePinsDefaults() throws IOException {
        WorldgenConfig config = new WorldgenConfig(SOURCE);
        config.getOrCreate(0);
        config.save(worldDir);

        CommentedConfigurationNode root = readRaw();
        assertEquals(
            256,
            root.node("dimensions", "0", "cctest", "range", "max")
                .getInt());
        assertEquals(
            "minecraft:stone",
            root.node("dimensions", "0", "cctest", "nested", "block", "block")
                .getString());
        assertEquals(
            -50,
            root.node("dimensions", "0", "cctest", "strata", 0, "full")
                .getInt());
    }

    @Test
    void readsHandWrittenFile() throws IOException {
        write("dimensions { \"0\" { cctest { range { min = -10, max = unbounded } } } }");

        WorldgenConfig config = WorldgenConfig.load(worldDir, SOURCE);
        assertEquals(
            new IntRange(-10, 1000),
            config.get(0)
                .get(RANGE));
    }

    @Test
    void roundsOutwardToAlignment() throws IOException {
        write("dimensions { \"0\" { cctest { aligned { min = -70, max = 70 } } } }");

        WorldgenConfig config = WorldgenConfig.load(worldDir, SOURCE);
        WGDimensionConfig dim = config.get(0);
        assertEquals(new IntRange(-80, 80), dim.get(ALIGNED));

        dim.set(ALIGNED, new IntRange(17, 1000));
        assertEquals(new IntRange(16, 1000), dim.get(ALIGNED));

        // Rounding never leaves the limits
        dim.set(ALIGNED, new IntRange(-999, 999));
        assertEquals(new IntRange(-1000, 1000), dim.get(ALIGNED));
        dim.set(ALIGNED, new IntRange(-1000, -990));
        assertEquals(new IntRange(-1000, -976), dim.get(ALIGNED));
    }

    @Test
    void strataRoundTrip() throws ConfigurateException {
        List<Stratum> strata = Arrays.asList(
            new Stratum(new BlockRef("minecraft:dirt", 0), 10, 20),
            new Stratum(new BlockRef("othermod:rock", 3), -100, -60));

        WorldgenConfig config = new WorldgenConfig(SOURCE);
        config.getOrCreate(0)
            .set(STRATA, strata);
        config.getOrCreate(1)
            .set(STRATA, Collections.emptyList());
        config.save(worldDir);

        WorldgenConfig loaded = WorldgenConfig.load(worldDir, SOURCE);
        assertEquals(
            strata,
            loaded.get(0)
                .get(STRATA));
        // An empty list is a real value, not "use the default"
        assertEquals(
            Collections.emptyList(),
            loaded.get(1)
                .get(STRATA));
    }

    @Test
    void readsHandWrittenStrataAndBlocks() throws IOException {
        write(
            "dimensions { \"0\" { cctest {\n" + "  strata = [ { block = \"minecraft:sand\", full = 5, none = 9 } ]\n"
                + "  nested { block { block = \"othermod:missing\" } }\n"
                + "} } }");

        WGDimensionConfig dim = WorldgenConfig.load(worldDir, SOURCE)
            .get(0);
        assertEquals(Collections.singletonList(new Stratum(new BlockRef("minecraft:sand", 0), 5, 9)), dim.get(STRATA));
        // Unknown blocks load; whatever uses the setting decides what to do with them
        assertEquals(new BlockRef("othermod:missing", 0), dim.get(BLOCK));
    }

    @Test
    void badStrataFail() throws IOException {
        write("dimensions { \"0\" { cctest { strata = [ { block = \"minecraft:sand\", full = 5 } ] } } }");
        assertThrows(ConfigurateException.class, () -> WorldgenConfig.load(worldDir, SOURCE));

        write("dimensions { \"0\" { cctest { nested { block { block = \"minecraft:wool\", meta = 16 } } } } }");
        assertThrows(ConfigurateException.class, () -> WorldgenConfig.load(worldDir, SOURCE));
    }

    @Test
    void unboundedIsTheLimit() throws IOException {
        WorldgenConfig config = new WorldgenConfig(SOURCE);
        WGDimensionConfig dim = config.getOrCreate(0);
        // Anything past the limits is clamped to them
        dim.set(RANGE, new IntRange(Integer.MIN_VALUE, 5000));
        assertEquals(new IntRange(-1000, 1000), dim.get(RANGE));
        config.save(worldDir);

        CommentedConfigurationNode root = readRaw();
        assertEquals(
            IntRangeSerializer.UNBOUNDED,
            root.node("dimensions", "0", "cctest", "range", "min")
                .getString());
        assertEquals(
            IntRangeSerializer.UNBOUNDED,
            root.node("dimensions", "0", "cctest", "range", "max")
                .getString());
    }

    @Test
    void autoBlockRoundTrip() throws IOException {
        write(
            "dimensions { \"0\" { cctest { filler = auto } }, \"1\" { cctest { filler { block = \"minecraft:dirt\" } } } }");

        WorldgenConfig config = WorldgenConfig.load(worldDir, SOURCE);
        assertNull(
            config.get(0)
                .get(FILLER));
        assertEquals(
            new BlockRef("minecraft:dirt", 0),
            config.get(1)
                .get(FILLER));

        // Storing a detected value replaces "auto" with the block
        config.get(0)
            .set(FILLER, new BlockRef("minecraft:stone", 1));
        config.get(1)
            .set(FILLER, null);
        config.save(worldDir);

        CommentedConfigurationNode root = readRaw();
        assertEquals(
            "minecraft:stone",
            root.node("dimensions", "0", "cctest", "filler", "block")
                .getString());
        assertEquals(
            BlockRefSerializer.AUTO,
            root.node("dimensions", "1", "cctest", "filler")
                .getString());
    }

    @Test
    void dimensionsOnlyHaveGatheredItems() throws IOException {
        // Dimension 1 doesn't use RANGE, e.g. because its generator ignores it
        WGConfigItemSource source = dimensionId -> dimensionId == 1 ? Collections.singletonList(BLOCK) : ITEMS;
        write("dimensions { \"1\" { cctest { range { min = 1, max = 2 } } } }");

        WorldgenConfig config = WorldgenConfig.load(worldDir, source);
        WGDimensionConfig dim = config.get(1);
        assertEquals(Collections.singletonList(BLOCK), dim.getItems());
        assertFalse(dim.hasItem(RANGE));
        assertThrows(IllegalArgumentException.class, () -> dim.get(RANGE));
        assertThrows(IllegalArgumentException.class, () -> dim.set(RANGE, new IntRange(0, 16)));

        // The ungathered value is kept in the file
        config.save(worldDir);
        CommentedConfigurationNode root = readRaw();
        assertEquals(
            2,
            root.node("dimensions", "1", "cctest", "range", "max")
                .getInt());
        assertTrue(
            root.node("dimensions", "1", "cctest", "nested", "block", "block")
                .getString() != null);
    }

    @Test
    void gatherRejectsDuplicates() {
        GatherWorldgenConfigEvent event = new GatherWorldgenConfigEvent(0);
        event.add(RANGE);
        event.add(BLOCK);
        assertThrows(IllegalArgumentException.class, () -> event.add(RANGE));
        assertEquals(Arrays.asList(RANGE, BLOCK), event.getItems());
    }

    @Test
    void preservesUnknownKeys() throws IOException {
        write("dimensions { \"0\" { othermod { foo = 1 }, cctest { range { min = 1, max = 2 } } } }");

        WorldgenConfig config = WorldgenConfig.load(worldDir, SOURCE);
        config.get(0)
            .set(RANGE, new IntRange(3, 4));
        config.save(worldDir);

        CommentedConfigurationNode root = readRaw();
        assertEquals(
            1,
            root.node("dimensions", "0", "othermod", "foo")
                .getInt());
        assertEquals(
            4,
            root.node("dimensions", "0", "cctest", "range", "max")
                .getInt());
    }

    @Test
    void outOfLimitsFailsWithPath() throws IOException {
        write("dimensions { \"0\" { cctest { range { min = 5000, max = unbounded } } } }");

        ConfigurateException e = assertThrows(ConfigurateException.class, () -> WorldgenConfig.load(worldDir, SOURCE));
        assertTrue(
            e.getMessage()
                .contains("min"),
            e.getMessage());
    }

    @Test
    void minAboveMaxFails() throws IOException {
        write("dimensions { \"0\" { cctest { range { min = 10, max = 5 } } } }");

        assertThrows(ConfigurateException.class, () -> WorldgenConfig.load(worldDir, SOURCE));
    }

    @Test
    void badDimensionKeyFails() throws IOException {
        write("dimensions { overworld { } }");

        assertThrows(ConfigurateException.class, () -> WorldgenConfig.load(worldDir, SOURCE));
    }

    @Test
    void newerVersionFails() throws IOException {
        write("version = " + (WorldgenConfig.VERSION + 1));

        assertThrows(ConfigurateException.class, () -> WorldgenConfig.load(worldDir, SOURCE));
    }

    private void write(String contents) throws IOException {
        File file = WorldgenConfig.getFile(worldDir);
        file.getParentFile()
            .mkdirs();
        Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));
    }

    private CommentedConfigurationNode readRaw() throws ConfigurateException {
        return HoconConfigurationLoader.builder()
            .file(WorldgenConfig.getFile(worldDir))
            .build()
            .load();
    }
}
