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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

import com.cardinalstar.cubicchunks.api.DimensionNames;
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
    private static final WGConfigItemSource SOURCE = (dimensionId, dimensionName) -> ITEMS;

    @TempDir
    File worldDir;

    /** The vanilla dimensions; tests name others as they need. */
    private final DimensionNames names = new DimensionNames();

    WorldgenConfigTest() {
        names.registerVanilla();
    }

    @Test
    void missingFileLoadsEmpty() throws ConfigurateException {
        WorldgenConfig config = WorldgenConfig.load(worldDir, SOURCE, names);
        assertTrue(
            config.getDimensionIds()
                .isEmpty());
        assertNull(config.get(0));
    }

    @Test
    void roundTrip() throws ConfigurateException {
        WorldgenConfig config = new WorldgenConfig(SOURCE, names);
        WGDimensionConfig overworld = config.getOrCreate(0);
        overworld.set(RANGE, new IntRange(-64, 1000));
        overworld.set(BLOCK, new BlockRef("minecraft:dirt", 1));
        config.getOrCreate(-1)
            .set(LIST, new String[] { "a", "b" });
        config.save(worldDir);

        WorldgenConfig loaded = WorldgenConfig.load(worldDir, SOURCE, names);
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
        WorldgenConfig config = new WorldgenConfig(SOURCE, names);
        config.getOrCreate(0);
        config.save(worldDir);

        CommentedConfigurationNode root = readRaw();
        assertEquals(
            256,
            root.node("dimensions", "minecraft:overworld", "cctest", "range", "max")
                .getInt());
        assertEquals(
            "minecraft:stone",
            root.node("dimensions", "minecraft:overworld", "cctest", "nested", "block", "block")
                .getString());
        assertEquals(
            -50,
            root.node("dimensions", "minecraft:overworld", "cctest", "strata", 0, "full")
                .getInt());
    }

    @Test
    void readsHandWrittenFile() throws IOException {
        write("dimensions { \"minecraft:overworld\" { cctest { range { min = -10, max = unbounded } } } }");

        WorldgenConfig config = WorldgenConfig.load(worldDir, SOURCE, names);
        assertEquals(
            new IntRange(-10, 1000),
            config.get(0)
                .get(RANGE));
    }

    @Test
    void roundsOutwardToAlignment() throws IOException {
        write("dimensions { \"minecraft:overworld\" { cctest { aligned { min = -70, max = 70 } } } }");

        WorldgenConfig config = WorldgenConfig.load(worldDir, SOURCE, names);
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

        WorldgenConfig config = new WorldgenConfig(SOURCE, names);
        config.getOrCreate(0)
            .set(STRATA, strata);
        config.getOrCreate(1)
            .set(STRATA, Collections.emptyList());
        config.save(worldDir);

        WorldgenConfig loaded = WorldgenConfig.load(worldDir, SOURCE, names);
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
            "dimensions { \"minecraft:overworld\" { cctest {\n"
                + "  strata = [ { block = \"minecraft:sand\", full = 5, none = 9 } ]\n"
                + "  nested { block { block = \"othermod:missing\" } }\n"
                + "} } }");

        WGDimensionConfig dim = WorldgenConfig.load(worldDir, SOURCE, names)
            .get(0);
        assertEquals(Collections.singletonList(new Stratum(new BlockRef("minecraft:sand", 0), 5, 9)), dim.get(STRATA));
        // Unknown blocks load; whatever uses the setting decides what to do with them
        assertEquals(new BlockRef("othermod:missing", 0), dim.get(BLOCK));
    }

    @Test
    void badStrataFail() throws IOException {
        write(
            "dimensions { \"minecraft:overworld\" { cctest { strata = [ { block = \"minecraft:sand\", full = 5 } ] } } }");
        assertThrows(ConfigurateException.class, () -> WorldgenConfig.load(worldDir, SOURCE, names));

        write(
            "dimensions { \"minecraft:overworld\" { cctest { nested { block { block = \"minecraft:wool\", meta = -1 } } } } }");
        assertThrows(ConfigurateException.class, () -> WorldgenConfig.load(worldDir, SOURCE, names));
    }

    @Test
    void extendedMetaLoads() throws IOException {
        // Metadata extenders allow more than 4 bits of block meta
        write(
            "dimensions { \"minecraft:overworld\" { cctest { nested { block { block = \"minecraft:wool\", meta = 1000000 } } } } }");
        WGDimensionConfig dim = WorldgenConfig.load(worldDir, SOURCE, names)
            .getOrCreate(0);
        assertEquals(new BlockRef("minecraft:wool", 1000000), dim.get(BLOCK));
    }

    @Test
    void unboundedIsTheLimit() throws IOException {
        WorldgenConfig config = new WorldgenConfig(SOURCE, names);
        WGDimensionConfig dim = config.getOrCreate(0);
        // Anything past the limits is clamped to them
        dim.set(RANGE, new IntRange(Integer.MIN_VALUE, 5000));
        assertEquals(new IntRange(-1000, 1000), dim.get(RANGE));
        config.save(worldDir);

        CommentedConfigurationNode root = readRaw();
        assertEquals(
            IntRangeSerializer.UNBOUNDED,
            root.node("dimensions", "minecraft:overworld", "cctest", "range", "min")
                .getString());
        assertEquals(
            IntRangeSerializer.UNBOUNDED,
            root.node("dimensions", "minecraft:overworld", "cctest", "range", "max")
                .getString());
    }

    @Test
    void autoBlockRoundTrip() throws IOException {
        write(
            "dimensions { \"minecraft:overworld\" { cctest { filler = auto } }, \"minecraft:the_end\" { cctest { filler { block = \"minecraft:dirt\" } } } }");

        WorldgenConfig config = WorldgenConfig.load(worldDir, SOURCE, names);
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
            root.node("dimensions", "minecraft:overworld", "cctest", "filler", "block")
                .getString());
        assertEquals(
            BlockRefSerializer.AUTO,
            root.node("dimensions", "minecraft:the_end", "cctest", "filler")
                .getString());
    }

    @Test
    void dimensionsOnlyHaveGatheredItems() throws IOException {
        // Dimension 1 doesn't use RANGE, e.g. because its generator ignores it
        WGConfigItemSource source = (dimensionId, dimensionName) -> dimensionId == 1 ? Collections.singletonList(BLOCK)
            : ITEMS;
        write("dimensions { \"minecraft:the_end\" { cctest { range { min = 1, max = 2 } } } }");

        WorldgenConfig config = WorldgenConfig.load(worldDir, source, names);
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
            root.node("dimensions", "minecraft:the_end", "cctest", "range", "max")
                .getInt());
        assertTrue(
            root.node("dimensions", "minecraft:the_end", "cctest", "nested", "block", "block")
                .getString() != null);
    }

    @Test
    void gatherRejectsDuplicates() {
        GatherWorldgenConfigEvent event = new GatherWorldgenConfigEvent(0, null);
        event.add(RANGE);
        event.add(BLOCK);
        assertThrows(IllegalArgumentException.class, () -> event.add(RANGE));
        assertEquals(Arrays.asList(RANGE, BLOCK), event.getItems());
    }

    @Test
    void preservesUnknownKeys() throws IOException {
        write("dimensions { \"minecraft:overworld\" { othermod { foo = 1 }, cctest { range { min = 1, max = 2 } } } }");

        WorldgenConfig config = WorldgenConfig.load(worldDir, SOURCE, names);
        config.get(0)
            .set(RANGE, new IntRange(3, 4));
        config.save(worldDir);

        CommentedConfigurationNode root = readRaw();
        assertEquals(
            1,
            root.node("dimensions", "minecraft:overworld", "othermod", "foo")
                .getInt());
        assertEquals(
            4,
            root.node("dimensions", "minecraft:overworld", "cctest", "range", "max")
                .getInt());
    }

    @Test
    void outOfLimitsFailsWithPath() throws IOException {
        write("dimensions { \"minecraft:overworld\" { cctest { range { min = 5000, max = unbounded } } } }");

        ConfigurateException e = assertThrows(
            ConfigurateException.class,
            () -> WorldgenConfig.load(worldDir, SOURCE, names));
        assertTrue(
            e.getMessage()
                .contains("min"),
            e.getMessage());
    }

    @Test
    void minAboveMaxFails() throws IOException {
        write("dimensions { \"minecraft:overworld\" { cctest { range { min = 10, max = 5 } } } }");

        assertThrows(ConfigurateException.class, () -> WorldgenConfig.load(worldDir, SOURCE, names));
    }

    @Test
    void savedUnderName() throws IOException {
        WorldgenConfig config = new WorldgenConfig(SOURCE, names);
        config.getOrCreate(-1)
            .set(RANGE, new IntRange(1, 2));
        config.save(worldDir);

        CommentedConfigurationNode root = readRaw();
        assertEquals(
            2,
            root.node("dimensions", "minecraft:the_nether", "cctest", "range", "max")
                .getInt());

        WorldgenConfig loaded = WorldgenConfig.load(worldDir, SOURCE, names);
        assertEquals(
            new IntRange(1, 2),
            loaded.get(-1)
                .get(RANGE));
    }

    @Test
    void unnamedDimensionsAreNotSaved() throws IOException {
        WorldgenConfig config = new WorldgenConfig(SOURCE, names);
        WGDimensionConfig dim = config.getOrCreate(5);
        // They still have settings while the game runs
        dim.set(RANGE, new IntRange(1, 2));
        assertEquals(new IntRange(1, 2), dim.get(RANGE));
        config.save(worldDir);

        assertTrue(
            readRaw().node("dimensions")
                .childrenMap()
                .isEmpty());
    }

    @Test
    void shortNamesFail() throws IOException {
        // Would be saved under minecraft:the_nether, leaving this key behind
        write("dimensions { the_nether { } }");

        assertThrows(ConfigurateException.class, () -> WorldgenConfig.load(worldDir, SOURCE, names));
    }

    @Test
    void nameFollowsItsDimensionToANewId() throws IOException {
        write("dimensions { \"othermod:moon\" { cctest { range { min = 1, max = 2 } } } }");
        // e.g. the modpack moved the dimension from id 7 to id 20
        names.register(20, new ResourceLocation("othermod", "moon"), null);

        WorldgenConfig config = WorldgenConfig.load(worldDir, SOURCE, names);
        assertEquals(Collections.singleton(20), config.getDimensionIds());
        assertEquals(
            new IntRange(1, 2),
            config.get(20)
                .get(RANGE));
    }

    @Test
    void unregisteredNamesAreKept() throws IOException {
        write(
            "dimensions { \"othermod:gone\" { cctest { range { min = 1, max = 2 } } }, \"minecraft:overworld\" { } }");

        WorldgenConfig config = WorldgenConfig.load(worldDir, SOURCE, names);
        assertEquals(Collections.singleton(0), config.getDimensionIds());
        config.save(worldDir);

        assertEquals(
            2,
            readRaw().node("dimensions", "othermod:gone", "cctest", "range", "max")
                .getInt());
    }

    @Test
    void sourceGetsDimensionName() {
        names.register(-1, DimensionNames.THE_NETHER, null);
        List<ResourceLocation> gathered = new ArrayList<>();
        WGConfigItemSource source = (dimensionId, dimensionName) -> {
            gathered.add(dimensionName);
            return ITEMS;
        };

        WorldgenConfig config = new WorldgenConfig(source, names);
        assertEquals(DimensionNames.THE_NETHER, config.getOrCreate(-1).dimensionName);
        assertNull(config.getOrCreate(5).dimensionName);
        assertEquals(Arrays.asList(DimensionNames.THE_NETHER, null), gathered);
    }

    @Test
    void reregisteringReplacesDisplayName() {
        ResourceLocation station = new ResourceLocation("othermod", "space_station_12");
        names.register(12, station, new ChatComponentText("Space Station 12"));
        names.register(12, station, new ChatComponentText("Home"));
        assertEquals(
            "Home",
            names.getDisplayName(12)
                .getUnformattedText());

        // Callers get a copy, so they can't change the registered name
        names.getDisplayName(12)
            .appendText("!");
        assertEquals(
            "Home",
            names.getDisplayName(12)
                .getUnformattedText());

        names.register(12, station, null);
        assertNull(names.getDisplayName(12));
    }

    @Test
    void conflictingDimensionNamesFail() {
        names.register(-1, DimensionNames.THE_NETHER, null);
        // Registering the same pair again is fine
        names.register(-1, DimensionNames.THE_NETHER, null);
        assertThrows(IllegalArgumentException.class, () -> names.register(-1, DimensionNames.THE_END, null));
        assertThrows(IllegalArgumentException.class, () -> names.register(7, DimensionNames.THE_NETHER, null));
        assertEquals(Integer.valueOf(-1), names.getId(DimensionNames.THE_NETHER));
        assertNull(names.getName(7));
    }

    @Test
    void newerVersionFails() throws IOException {
        write("version = " + (WorldgenConfig.VERSION + 1));

        assertThrows(ConfigurateException.class, () -> WorldgenConfig.load(worldDir, SOURCE, names));
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
