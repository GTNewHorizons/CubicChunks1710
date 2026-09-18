package com.cardinalstar.cubicchunks.mixin.early.client;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.storage.SaveFormatComparator;

import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.client.gui.worldselect.WorldFormatBadgeRenderer;
import com.cardinalstar.cubicchunks.world.convert.WorldSaveFormat;

/**
 * Draws the format badge ([CC] / [Vanilla] / [Mixed]) in each world-list slot.
 * Targets the private inner class {@code GuiSelectWorld$List}.
 */
@ParametersAreNonnullByDefault
@Mixin(targets = "net.minecraft.client.gui.GuiSelectWorld$List")
public abstract class MixinGuiSelectWorldList {

    /**
     * Assumption: {@code mc.currentScreen} is always a {@link GuiSelectWorld} when
     * {@code drawSlot} is executing.
     */
    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void drawFormatBadge(int index, int x, int y, int height,
                                  Tessellator tessellator, int mouseX, int mouseY,
                                  CallbackInfo ci) {

        Minecraft mc = Minecraft.getMinecraft();

        GuiSlot upper = (GuiSlot) (Object) this;

        if (!(mc.currentScreen instanceof GuiSelectWorld)) return;

        GuiSelectWorld gsw = (GuiSelectWorld) mc.currentScreen;
        List<SaveFormatComparator> worlds = ((IGuiSelectWorld) gsw).getWorldList();

        if (index < 0 || index >= worlds.size()) return;

        String fileName = worlds.get(index).getFileName();
        WorldSaveFormat format = WorldFormatBadgeRenderer.getCachedFormat(mc, fileName);

        WorldFormatBadgeRenderer.drawBadge(((AccessorGuiScreen) gsw).getFontRendererObj(), format, x, y, upper.getListWidth());
    }
}
