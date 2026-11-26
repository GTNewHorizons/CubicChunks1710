package com.cardinalstar.cubicchunks.worldgen.config;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;

import com.cardinalstar.cubicchunks.api.DimensionNames;

/// One world's worldgen settings, stored in `<world>/cubicchunks/worldgen.conf` so they travel with the save.
///
/// File layout: `dimensions { "<dim name>" { <item domain> { <item path> = ... } } }`. Dimensions are keyed by
/// their full [`name`][DimensionNames], e.g. `minecraft:the_nether`, since ids can change between modpack
/// versions. Dimensions without a name use the defaults, and their settings aren't saved. A dimension's generator is
/// stored as `cubicchunks.generator`, which no item may use.
///
/// Which settings a dimension has comes from a [WGConfigItemSource]. Keys none of them use, and dimensions whose
/// name isn't registered (e.g. from a mod that has since been removed), are kept when the file is rewritten.
public class WorldgenConfig {

    public static final int VERSION = 1;

    private static final Object[] GENERATOR_PATH = { "cubicchunks", "generator" };

    private static final String HEADER = "Cubic Chunks world generation settings for this world.\n"
        + "Changing these after chunks have been generated only affects new chunks, and will leave visible seams\n"
        + "where new terrain meets old terrain.\n"
        + "Settings are grouped by dimension name, then by the mod that owns them.";

    public static File getFile(File worldDir) {
        return new File(worldDir, "cubicchunks/worldgen.conf");
    }

    private final WGConfigItemSource itemSource;
    private final DimensionNames names;
    private final Map<Integer, WGDimensionConfig> dimensions = new TreeMap<>();
    /// The file as loaded, so unknown keys survive a save.
    private final CommentedConfigurationNode root;

    public WorldgenConfig(WGConfigItemSource itemSource, DimensionNames names) {
        this(itemSource, names, CommentedConfigurationNode.root());
    }

    private WorldgenConfig(WGConfigItemSource itemSource, DimensionNames names, CommentedConfigurationNode root) {
        this.itemSource = itemSource;
        this.names = names;
        this.root = root;
    }

    /// Dimensions without a name get settings too, so they have defaults, but they aren't saved. New settings use the
    /// Automatic generator.
    public WGDimensionConfig getOrCreate(int dimensionId) {
        return dimensions.computeIfAbsent(dimensionId, id -> create(id, null));
    }

    /// Selects a dimension's generator, which replaces its settings with ones gathered for that generator. Values of
    /// settings the new generator still has are kept. Returns the new settings.
    public WGDimensionConfig setGenerator(int dimensionId, ResourceLocation generator) {
        WGDimensionConfig dim = create(dimensionId, generator);
        dim.copyValuesFrom(getOrCreate(dimensionId));
        dimensions.put(dimensionId, dim);
        return dim;
    }

    private WGDimensionConfig create(int dimensionId, @Nullable ResourceLocation generator) {
        ResourceLocation name = names.getName(dimensionId);
        return new WGDimensionConfig(dimensionId, name, generator, itemSource.getItems(dimensionId, name, generator));
    }

    /// Null if this world has no settings for the dimension.
    public WGDimensionConfig get(int dimensionId) {
        return dimensions.get(dimensionId);
    }

    public Set<Integer> getDimensionIds() {
        return Collections.unmodifiableSet(dimensions.keySet());
    }

    /// Loads the world's settings, or returns an empty config if it has no file. Throws on any invalid value
    /// rather than falling back to defaults, since generating with the wrong settings is worse than not loading.
    public static WorldgenConfig load(File worldDir, WGConfigItemSource itemSource, DimensionNames names)
        throws ConfigurateException {
        File file = getFile(worldDir);
        if (!file.exists()) return new WorldgenConfig(itemSource, names);

        CommentedConfigurationNode root = loader(file).load();

        int version = root.node("version")
            .getInt(VERSION);
        if (version > VERSION) {
            throw new ConfigurateException(
                root.node("version"),
                file + " is from a newer version of Cubic Chunks (format " + version + ", supported: " + VERSION + ")");
        }

        WorldgenConfig config = new WorldgenConfig(itemSource, names, root);
        for (Map.Entry<Object, CommentedConfigurationNode> entry : root.node("dimensions")
            .childrenMap()
            .entrySet()) {
            ResourceLocation name = parseFullName(
                entry.getKey()
                    .toString(),
                entry.getValue(),
                "dimension");
            Integer dimensionId = names.getId(name);
            // A name nobody registered, e.g. from a mod that has been removed: kept as it is
            if (dimensionId == null) continue;

            // Read first, since it decides which items the dimension has
            ConfigurationNode generatorNode = entry.getValue()
                .node(GENERATOR_PATH);
            ResourceLocation generator = generatorNode.virtual() ? null
                : parseFullName(generatorNode.getString(""), generatorNode, "generator");

            WGDimensionConfig dim = config.create(dimensionId, generator);
            config.dimensions.put(dimensionId, dim);
            for (WGConfigItem<?> item : dim.getItems()) {
                readItem(item, dim, entry.getValue());
            }
        }
        return config;
    }

    /// Writes the value of every setting of every dimension in this config.
    public void save(File worldDir) throws ConfigurateException {
        root.node("version")
            .set(VERSION);
        ConfigurationNode dimsNode = root.node("dimensions");
        for (Map.Entry<Integer, WGDimensionConfig> entry : dimensions.entrySet()) {
            ResourceLocation name = entry.getValue().dimensionName;
            if (name == null) continue;

            ConfigurationNode dimNode = dimsNode.node(name.toString());
            ResourceLocation generator = entry.getValue().generator;
            if (generator != null) {
                dimNode.node(GENERATOR_PATH)
                    .set(generator.toString());
            }
            for (WGConfigItem<?> item : entry.getValue()
                .getItems()) {
                writeItem(item, entry.getValue(), dimNode);
            }
        }

        File file = getFile(worldDir);
        file.getParentFile()
            .mkdirs();
        loader(file).save(root);
    }

    /// Parses a name that must be written in full, since anything else would be saved under a different key.
    private static ResourceLocation parseFullName(String text, ConfigurationNode node, String what)
        throws ConfigurateException {
        ResourceLocation name = new ResourceLocation(text);
        if (!name.toString()
            .equals(text)) {
            throw new ConfigurateException(
                node,
                "expected a full " + what + " name, e.g. '" + name + "', got '" + text + "'");
        }
        return name;
    }

    private static HoconConfigurationLoader loader(File file) {
        return HoconConfigurationLoader.builder()
            .file(file)
            .defaultOptions(options -> options.header(HEADER))
            .build();
    }

    private static ConfigurationNode itemNode(ConfigurationNode dimNode, WGConfigItem<?> item) {
        ResourceLocation id = item.getId();
        return dimNode.node(id.getResourceDomain())
            .node(
                (Object[]) id.getResourcePath()
                    .split("/"));
    }

    private static <T> void readItem(WGConfigItem<T> item, WGDimensionConfig dim, ConfigurationNode dimNode)
        throws SerializationException {
        ConfigurationNode node = itemNode(dimNode, item);
        if (node.virtual()) return;

        WGConfigItemType<T> type = item.getConfigItemType();
        try {
            dim.set(
                item,
                type.getSerializer()
                    .deserialize(type.getType(), node));
        } catch (SerializationException e) {
            e.initPath(node::path);
            throw e;
        }
    }

    private static <T> void writeItem(WGConfigItem<T> item, WGDimensionConfig dim, ConfigurationNode dimNode)
        throws SerializationException {
        WGConfigItemType<T> type = item.getConfigItemType();
        type.getSerializer()
            .serialize(type.getType(), dim.get(item), itemNode(dimNode, item));
    }
}
