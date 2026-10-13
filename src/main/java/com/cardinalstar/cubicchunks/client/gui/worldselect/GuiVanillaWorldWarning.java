package com.cardinalstar.cubicchunks.client.gui.worldselect;

import java.io.File;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.resources.I18n;

import com.cardinalstar.cubicchunks.client.gui.convert.GuiConvertWorld;
import com.cardinalstar.cubicchunks.mixin.early.client.AccessorGuiSelectWorld;
import com.cardinalstar.cubicchunks.world.convert.WorldFormatDetector;
import com.cardinalstar.cubicchunks.world.convert.WorldSaveFormat;

/**
 * Warning shown when the player attempts to load a vanilla-format world while
 * CubicChunks is active. Vanilla chunk data is ignored by CC, so the user should
 * convert before loading or acknowledge the consequence.
 */
@ParametersAreNonnullByDefault
public class GuiVanillaWorldWarning extends GuiScreen {

    private static final int BTN_LOAD_ANYWAY = 0;
    private static final int BTN_CONVERT     = 1;
    private static final int BTN_CANCEL      = 2;

    private final GuiSelectWorld worldSelectParent;
    private final int worldIndex;
    private final WorldSaveFormat format;

    /**
     * @param worldSelectParent the world-select screen to return to on cancel
     * @param worldIndex        index of the world that was selected
     * @param format            detected format ({@link WorldSaveFormat#VANILLA} or {@link WorldSaveFormat#MIXED})
     */
    public GuiVanillaWorldWarning(GuiSelectWorld worldSelectParent, int worldIndex, WorldSaveFormat format) {
        this.worldSelectParent = worldSelectParent;
        this.worldIndex = worldIndex;
        this.format = format;
    }

    @Override
    public void initGui() {
        buttonList.clear();

        buttonList.add(new GuiButton(BTN_LOAD_ANYWAY, width / 2 - 154, height - 38, 100, 20,
            I18n.format("cubicchunks.gui.warning.vanilla_world.load_anyway")));
        buttonList.add(new GuiButton(BTN_CONVERT, width / 2 - 50, height - 38, 100, 20,
            I18n.format("cubicchunks.gui.warning.vanilla_world.convert")));
        buttonList.add(new GuiButton(BTN_CANCEL, width / 2 + 54, height - 38, 100, 20,
            I18n.format("gui.cancel")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BTN_LOAD_ANYWAY:
                ((ICCGuiSelectWorld) worldSelectParent).cubicchunks$setBypassFormatWarning(true);
                ((ICCGuiSelectWorld) worldSelectParent).cubicchunks$loadWorld(worldIndex);
                break;

            case BTN_CONVERT: {
                String fileName = ((AccessorGuiSelectWorld) worldSelectParent).invokeFunc_146621_a(worldIndex);
                File worldDir = mc.getSaveLoader().getSaveLoader(fileName, false).getWorldDirectory();
                mc.displayGuiScreen(new GuiConvertWorld(worldSelectParent, fileName, worldDir, format));
                break;
            }

            case BTN_CANCEL:
                mc.displayGuiScreen(worldSelectParent);
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        drawCenteredString(fontRendererObj,
            I18n.format("cubicchunks.gui.warning.vanilla_world.title"),
            width / 2, height / 2 - 30, 0xFF5555);

        drawCenteredString(fontRendererObj,
            I18n.format("cubicchunks.gui.warning.vanilla_world.body"),
            width / 2, height / 2 - 10, 0xFFFFFF);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
