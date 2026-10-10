package com.cardinalstar.cubicchunks.mixin.early.client;

import java.io.File;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.resources.I18n;
import net.minecraft.world.storage.ISaveHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cardinalstar.cubicchunks.client.gui.convert.GuiConvertWorld;
import com.cardinalstar.cubicchunks.client.gui.worldselect.GuiVanillaWorldWarning;
import com.cardinalstar.cubicchunks.client.gui.worldselect.ICCGuiSelectWorld;
import com.cardinalstar.cubicchunks.client.gui.worldselect.WorldFormatBadgeRenderer;
import com.cardinalstar.cubicchunks.world.convert.WorldFormatDetector;
import com.cardinalstar.cubicchunks.world.convert.WorldSaveFormat;

@ParametersAreNonnullByDefault
@Mixin(GuiSelectWorld.class)
public abstract class MixinGuiSelectWorld extends GuiScreen
    implements GuiYesNoCallback, ICCGuiSelectWorld {

    private static final int CC_CONVERT_BTN_ID = 10;

    @Shadow protected abstract String func_146621_a(int index);
    @Shadow private GuiButton field_146641_z; // Play Selected World
    @Shadow private int field_146640_r;        // selected index

    @Unique private GuiButton cubicchunks$convertButton;
    @Unique private boolean   cubicchunks$bypassFormatWarning = false;

    // region — ICCGuiSelectWorld

    @Override
    @Unique
    public void cubicchunks$setBypassFormatWarning(boolean value) {
        cubicchunks$bypassFormatWarning = value;
    }

    @Override
    @Unique
    public void cubicchunks$loadWorld(int index) {
        func_146615_e(index);
    }

    // endregion

    /**
     * Adds the "Convert" button to the first button row and clears the format badge
     * cache whenever the button layout is (re)initialised.
     */
    @Inject(method = "func_146618_g", at = @At("TAIL"))
    private void addConvertButton(CallbackInfo ci) {
        for (Object obj : buttonList) {
            GuiButton btn = (GuiButton) obj;
            if (btn.id == 1) { btn.width = 100; }
            if (btn.id == 3) { btn.xPosition = width / 2 + 54; btn.width = 100; }
        }

        cubicchunks$convertButton = new GuiButton(CC_CONVERT_BTN_ID,
            width / 2 - 50, height - 52, 100, 20,
            I18n.format("cubicchunks.gui.worldselect.convert"));
        cubicchunks$convertButton.enabled = false;
        buttonList.add(cubicchunks$convertButton);

        WorldFormatBadgeRenderer.invalidate();
    }

    /** Keeps the Convert button's enabled state in sync with the Play button each frame. */
    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void syncConvertButton(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (cubicchunks$convertButton != null && field_146641_z != null) {
            cubicchunks$convertButton.enabled = field_146641_z.enabled;
        }
    }

    /** Opens the conversion screen when the Convert button is clicked. */
    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void handleConvertButton(GuiButton button, CallbackInfo ci) {
        if (button.id != CC_CONVERT_BTN_ID || !button.enabled) return;

        if (field_146640_r < 0) return;

        String fileName = func_146621_a(field_146640_r);
        ISaveHandler handler = mc.getSaveLoader().getSaveLoader(fileName, false);
        File worldDir = handler.getWorldDirectory();
        WorldSaveFormat format = WorldFormatDetector.detect(worldDir);

        mc.displayGuiScreen(new GuiConvertWorld((GuiSelectWorld) (Object) this, fileName, worldDir, format));
        ci.cancel();
    }

    /**
     * Shows a format warning before loading a vanilla-format world.
     * The bypass flag allows "Load Anyway" in the warning dialog to proceed without looping.
     *
     * <p>Assumption: {@code func_146615_e} is only called for a valid index.
     */
    @Inject(method = "func_146615_e", at = @At("HEAD"), cancellable = true)
    private void checkFormatBeforeLoad(int index, CallbackInfo ci) {
        if (cubicchunks$bypassFormatWarning) {
            cubicchunks$bypassFormatWarning = false;
            return;
        }

        String fileName = func_146621_a(index);
        ISaveHandler handler = mc.getSaveLoader().getSaveLoader(fileName, false);
        File worldDir = handler.getWorldDirectory();
        WorldSaveFormat fmt = WorldFormatDetector.detect(worldDir);

        if (fmt == WorldSaveFormat.VANILLA || fmt == WorldSaveFormat.MIXED) {
            mc.displayGuiScreen(new GuiVanillaWorldWarning((GuiSelectWorld) (Object) this, index, fmt));
            ci.cancel();
        }
    }

    /** Vanilla method exposed for {@link ICCGuiSelectWorld#cubicchunks$loadWorld}. */
    @Shadow
    protected abstract void func_146615_e(int index);
}
