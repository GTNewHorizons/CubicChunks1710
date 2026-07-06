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
package com.cardinalstar.cubicchunks.event.handlers;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionsRowList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiVideoSettings;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.CubicChunksConfig;
import com.cardinalstar.cubicchunks.api.compat.CubicChunksVideoSettings;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.mixin.early.client.IGuiOptionsRowList;
import com.cardinalstar.cubicchunks.mixin.early.client.IGuiVideoSettings;
import com.cardinalstar.cubicchunks.server.ICubicPlayerList;
import com.cardinalstar.cubicchunks.util.MathUtil;
import com.cardinalstar.cubicchunks.util.Mods;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

@ParametersAreNonnullByDefault
public class ClientEventHandler {

    @SubscribeEvent
    public void onWorldClientTickEvent(TickEvent.ClientTickEvent evt) {
        ICubicWorldInternal world = (ICubicWorldInternal) FMLClientHandler.instance()
            .getWorldClient();
        // does the world exist? Is the game paused?
        if (world == null || Minecraft.getMinecraft()
            .isGamePaused()) {
            return;
        }
        if (evt.phase == TickEvent.Phase.END) {
            world.tickCubicWorld();
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // no need to check side, this is only registered in client proxy
        ICubicPlayerList playerList = ((ICubicPlayerList) FMLCommonHandler.instance()
            .getMinecraftServerInstance()
            .getConfigurationManager());
        int prevDist = playerList.getVerticalViewDistance();
        int newDist = CubicChunksConfig.verticalCubeLoadDistance;
        if (prevDist != newDist) {
            CubicChunks.LOGGER.info("Changing vertical view distance to {}, from {}", newDist, prevDist);
            playerList.setVerticalViewDistance(newDist);
        }
    }

    @SubscribeEvent
    public void initGuiEvent(InitGuiEvent.Post event) {

        GuiScreen currentGui = event.gui;
        if (currentGui instanceof GuiVideoSettings && !Mods.Angelica.isModLoaded()) {
            GuiVideoSettings gvs = (GuiVideoSettings) currentGui;
            IGuiOptionsRowList gowl = (IGuiOptionsRowList) ((IGuiVideoSettings) gvs).getOptionsRowList();
            GuiOptionsRowList.Row row = this.createRow(100, gvs.width);
            gowl.getOptions()
                .add(1, row);
        }
    }

    private GuiOptionsRowList.Row createRow(int buttonId, int width) {
        VertViewDistanceSlider slider = new VertViewDistanceSlider(buttonId, width / 2 - 155 + 160, 0);
        return new GuiOptionsRowList.Row(slider, null);
    }

    private class VertViewDistanceSlider extends GuiButton {

        private final int minViewDist = CubicChunksVideoSettings.getMinVerticalViewDistance();
        private final int maxViewDist = CubicChunksVideoSettings.getMaxVerticalViewDistance();
        private float sliderValue;
        public boolean dragging;

        public VertViewDistanceSlider(int buttonId, int x, int y) {
            super(buttonId, x, y, 150, 20, "");
            this.sliderValue = getSliderValue();
            this.displayString = this.createDisplayString();
        }

        /**
         * Returns 0 if the button is disabled, 1 if the mouse is NOT hovering
         * over this button and 2 if it IS hovering over this button.
         */
        @Override
        public int getHoverState(boolean mouseOver) {
            return 0;
        }

        /**
         * Fired when the mouse button is dragged. Equivalent of
         * MouseListener.mouseDragged(MouseEvent e).
         */
        @Override
        protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
            if (this.visible) {
                if (this.dragging) {
                    this.sliderValue = (float) (mouseX - (this.xPosition + 4)) / (float) (this.width - 8);
                    this.sliderValue = MathHelper.clamp_float(this.sliderValue, 0.0F, 1.0F);
                    onSliderValueChanged();
                    this.sliderValue = getSliderValue();
                    this.displayString = this.createDisplayString();
                }

                mc.getTextureManager()
                    .bindTexture(buttonTextures);
                // GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F); // TODO FIGURE OUT HOW TO DO THIS
                this.drawTexturedModalRect(
                    this.xPosition + (int) (this.sliderValue * (float) (this.width - 8)),
                    this.yPosition,
                    0,
                    66,
                    4,
                    20);
                this.drawTexturedModalRect(
                    this.xPosition + (int) (this.sliderValue * (float) (this.width - 8)) + 4,
                    this.yPosition,
                    196,
                    66,
                    4,
                    20);
            }
        }

        /**
         * Returns true if the mouse has been pressed on this control.
         * Equivalent of MouseListener.mousePressed(MouseEvent e).
         */
        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                this.sliderValue = (float) (mouseX - (this.xPosition + 4)) / (float) (this.width - 8);
                this.sliderValue = MathHelper.clamp_float(this.sliderValue, 0.0F, 1.0F);
                onSliderValueChanged();
                this.sliderValue = getSliderValue();
                this.displayString = this.createDisplayString();
                this.dragging = true;
                return true;
            } else {
                return false;
            }
        }

        private void onSliderValueChanged() {
            CubicChunksVideoSettings
                .setVerticalViewDistance(Math.round(MathUtil.lerp(this.sliderValue, minViewDist, maxViewDist)));
        }

        private float getSliderValue() {
            return MathUtil.unlerp(CubicChunksVideoSettings.getVerticalViewDistance(), minViewDist, maxViewDist);
        }

        private String createDisplayString() {
            return I18n.format(
                CubicChunks.MODID + ".gui.vertical_cube_load_distance",
                CubicChunksVideoSettings.getVerticalViewDistance());
        }

        /**
         * Fired when the mouse button is released. Equivalent of
         * MouseListener.mouseReleased(MouseEvent e).
         */
        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            this.dragging = false;
        }
    }
}
