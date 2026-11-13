package com.cardinalstar.cubicchunks.worldgen.config.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.worldgen.config.BlockRef;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The blocks that can be picked in block settings, one per block + meta.
 * <p>
 * Variants come from item blocks' creative tab entries (the same source as the creative menu and NEI), so blocks with
 * subtypes get one entry per subtype with its proper name and icon. Blocks without an item (liquids, technical blocks)
 * are listed once, by registry name.
 */
@SideOnly(Side.CLIENT)
public final class BlockVariants {

    public static final class Variant {

        public final BlockRef ref;
        /** Null for blocks without an item. */
        public final ItemStack icon;
        public final String name;
        /** Lowercase name and id, for searching. */
        final String searchText;

        Variant(BlockRef ref, ItemStack icon, String name) {
            this.ref = ref;
            this.icon = icon;
            this.name = name;
            this.searchText = (name + " " + ref).toLowerCase();
        }

        public boolean matches(String lowercaseQuery) {
            return searchText.contains(lowercaseQuery);
        }
    }

    private static final RenderItem ITEM_RENDER = new RenderItem();

    private static List<Variant> variants;
    private static Map<BlockRef, Variant> byRef;

    private BlockVariants() {}

    /** All variants, sorted by registry name then meta. Built on first use. */
    public static List<Variant> all() {
        if (variants == null) build();
        return variants;
    }

    /** Null if {@code ref} is null or isn't a known variant. */
    public static Variant find(BlockRef ref) {
        if (ref == null) return null;
        if (byRef == null) build();
        return byRef.get(ref);
    }

    /** The variant's display name, or the raw id (marked if the block doesn't exist). Null is "auto-detect". */
    public static String label(BlockRef ref) {
        if (ref == null) return I18n.format("cubicchunks.wgconfig.block.auto");
        Variant variant = find(ref);
        if (variant != null) return variant.name;
        if (ref.resolve() == null) {
            return EnumChatFormatting.RED + ref.toString() + " " + I18n.format("cubicchunks.wgconfig.block.missing");
        }
        return ref.toString();
    }

    /** Draws {@code ref}'s item icon (16x16) at x, y, if it has one. */
    public static void renderIcon(BlockRef ref, int x, int y) {
        Variant variant = find(ref);
        if (variant == null || variant.icon == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        try {
            ITEM_RENDER.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), variant.icon, x, y);
        } finally {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL12.GL_RESCALE_NORMAL);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_LIGHTING);
        }
    }

    private static void build() {
        Map<BlockRef, Variant> found = new LinkedHashMap<>();

        for (Object obj : Item.itemRegistry) {
            if (!(obj instanceof ItemBlock)) continue;
            ItemBlock item = (ItemBlock) obj;
            String id = Block.blockRegistry.getNameForObject(item.field_150939_a);
            if (id == null) continue;

            for (ItemStack stack : subItems(item)) {
                // The meta the item places, as ItemBlock.onItemUse does
                int meta = item.getMetadata(stack.getItemDamage());
                if (meta < 0) continue;
                BlockRef ref = new BlockRef(id, meta);
                if (!found.containsKey(ref)) found.put(ref, new Variant(ref, stack, displayName(stack, ref)));
            }
        }

        for (Object obj : Block.blockRegistry) {
            Block block = (Block) obj;
            if (Item.getItemFromBlock(block) != null) continue;
            String id = Block.blockRegistry.getNameForObject(block);
            if (id == null) continue;
            BlockRef ref = new BlockRef(id, 0);
            if (!found.containsKey(ref)) found.put(ref, new Variant(ref, null, id));
        }

        List<Variant> sorted = new ArrayList<>(found.values());
        sorted.sort(
            Comparator.comparing((Variant v) -> v.ref.id)
                .thenComparingInt(v -> v.ref.meta));
        variants = Collections.unmodifiableList(sorted);
        byRef = new HashMap<>(found);
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> subItems(Item item) {
        List<ItemStack> stacks = new ArrayList<>();
        try {
            for (CreativeTabs tab : item.getCreativeTabs()) {
                item.getSubItems(item, tab, stacks);
            }
        } catch (Throwable t) {
            CubicChunks.LOGGER.debug("Could not list subtypes of {}", item, t);
        }

        List<ItemStack> valid = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (stack != null && stack.getItem() == item) valid.add(stack);
        }
        // Not in any creative tab: list the base item
        if (valid.isEmpty()) valid.add(new ItemStack(item));
        return valid;
    }

    private static String displayName(ItemStack stack, BlockRef ref) {
        try {
            String name = stack.getDisplayName();
            if (name != null && !name.isEmpty()) return name;
        } catch (Throwable t) {
            CubicChunks.LOGGER.debug("Could not get the name of {}", ref, t);
        }
        return ref.toString();
    }
}
