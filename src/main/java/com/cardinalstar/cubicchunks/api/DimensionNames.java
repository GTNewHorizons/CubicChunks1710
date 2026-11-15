package com.cardinalstar.cubicchunks.api;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

/**
 * Maps dimension ids to stable registry names, e.g. {@code -1} to {@code minecraft:the_nether}. Ids can differ between
 * modpack versions and servers, names don't, so anything saved per dimension should be keyed by name. Names match the
 * modern vanilla dimension ids, so datapacks can refer to the same dimensions.
 * <p>
 * Mods register their dimensions through {@link CCAPI#registerDimensionName}: static dimensions during init, dynamic
 * ones (space stations, personal dimensions) when they're created or loaded. A dimension nobody names has no name;
 * callers fall back to its id.
 * <p>
 * Display names are chat components rather than lang keys, so dynamic names like "Space Station 12" can be localized
 * and sent to clients as they are.
 */
public final class DimensionNames {

    public static final DimensionNames INSTANCE = new DimensionNames();

    public static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    public static final ResourceLocation THE_NETHER = new ResourceLocation("minecraft", "the_nether");
    public static final ResourceLocation THE_END = new ResourceLocation("minecraft", "the_end");

    private final Map<Integer, ResourceLocation> names = new HashMap<>();
    private final Map<ResourceLocation, Integer> ids = new HashMap<>();
    private final Map<Integer, IChatComponent> displayNames = new HashMap<>();

    /** Not for mods: use {@link #INSTANCE}. Separate instances are for tests. */
    public DimensionNames() {}

    /**
     * Names a dimension. Registering the same id and name again replaces the display name, e.g. when a player renames
     * their space station. Giving an id a second name, or a name to a second id, throws.
     *
     * @param displayName the dimension's display name, or null to show the registry name
     */
    public synchronized void register(int id, ResourceLocation name, @Nullable IChatComponent displayName) {
        ResourceLocation existingName = names.get(id);
        if (existingName != null && !existingName.equals(name)) {
            throw new IllegalArgumentException(
                "Dimension " + id + " is already named " + existingName + ", can't name it " + name);
        }
        Integer existingId = ids.get(name);
        if (existingId != null && existingId != id) {
            throw new IllegalArgumentException(
                "Dimension name " + name + " already belongs to dimension " + existingId + ", can't give it to " + id);
        }

        names.put(id, name);
        ids.put(name, id);
        if (displayName != null) {
            displayNames.put(id, displayName.createCopy());
        } else {
            displayNames.remove(id);
        }
    }

    /** Null if the dimension has no name. */
    @Nullable
    public synchronized ResourceLocation getName(int id) {
        return names.get(id);
    }

    /** Null if no dimension has this name. */
    @Nullable
    public synchronized Integer getId(ResourceLocation name) {
        return ids.get(name);
    }

    /** A copy of the dimension's display name, or null if it has none. */
    @Nullable
    public synchronized IChatComponent getDisplayName(int id) {
        IChatComponent displayName = displayNames.get(id);
        return displayName != null ? displayName.createCopy() : null;
    }

    /** Names the vanilla dimensions. */
    public void registerVanilla() {
        register(0, OVERWORLD, new ChatComponentTranslation("cubicchunks.dimension.overworld"));
        register(-1, THE_NETHER, new ChatComponentTranslation("cubicchunks.dimension.the_nether"));
        register(1, THE_END, new ChatComponentTranslation("cubicchunks.dimension.the_end"));
    }
}
