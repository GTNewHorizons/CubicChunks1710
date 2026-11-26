package com.cardinalstar.cubicchunks.mixin.early.client;

import java.io.File;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiErrorScreen;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.configurate.ConfigurateException;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.worldgen.config.WGConfigItemSource;
import com.cardinalstar.cubicchunks.worldgen.config.WorldgenConfig;
import com.cardinalstar.cubicchunks.worldgen.config.gui.WGDimensionListScreen;

/**
 * Adds a worldgen settings button to the "More World Options" page, and writes the settings into the new world's
 * folder just before the integrated server creates it.
 */
@Mixin(GuiCreateWorld.class)
public abstract class MixinGuiCreateWorld extends GuiScreen {

    /** Clear of vanilla's ids (0-8). */
    @Unique
    private static final int cc$WORLDGEN_BUTTON_ID = 9135;

    /** The new world's folder name. */
    @Shadow
    private String field_146336_i;

    /** Whether the "More World Options" page is showing. */
    @Shadow
    private boolean field_146344_y;

    /** The "More World Options" / "Done" toggle. */
    @Shadow
    private GuiButton field_146324_A;

    @Unique
    private final WorldgenConfig cc$worldgenConfig = new WorldgenConfig(WGConfigItemSource.EVENT);

    @Unique
    private GuiButton cc$worldgenButton;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void cc$addWorldgenButton(CallbackInfo ci) {
        cc$worldgenButton = new GuiButton(
            cc$WORLDGEN_BUTTON_ID,
            width / 2 - 155,
            187,
            150,
            20,
            I18n.format("cubicchunks.wgconfig.create_world_button"));
        // GuiScreen.mouseClicked keeps checking buttons after one handles the click. If ours came after the toggle,
        // clicking "More World Options" would show our button under the cursor and press it in the same click.
        buttonList.add(buttonList.indexOf(field_146324_A), cc$worldgenButton);
        cc$updateWorldgenButton();
    }

    @Inject(method = "func_146316_a", at = @At("TAIL"))
    private void cc$onShowMoreOptions(boolean show, CallbackInfo ci) {
        // initGui calls this before our button has been added
        if (cc$worldgenButton != null) cc$updateWorldgenButton();
    }

    @Unique
    private void cc$updateWorldgenButton() {
        cc$worldgenButton.visible = field_146344_y;
        // Share the bottom row with the toggle, which reads "Done" on this page
        field_146324_A.xPosition = field_146344_y ? width / 2 + 5 : width / 2 - 75;
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void cc$openWorldgenSettings(GuiButton button, CallbackInfo ci) {
        if (button.enabled && button.id == cc$WORLDGEN_BUTTON_ID) {
            mc.displayGuiScreen(new WGDimensionListScreen(this, cc$worldgenConfig));
            ci.cancel();
        }
    }

    @Inject(
        method = "actionPerformed",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;launchIntegratedServer(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/world/WorldSettings;)V"),
        cancellable = true)
    private void cc$saveWorldgenSettings(GuiButton button, CallbackInfo ci) {
        // Nothing was configured: leave the world to its defaults
        if (cc$worldgenConfig.getDimensionIds()
            .isEmpty()) return;

        File worldDir = new File(new File(mc.mcDataDir, "saves"), field_146336_i);
        // The folder name is made unique as the name is typed, but never overwrite an existing world's settings
        if (new File(worldDir, "level.dat").exists()) {
            CubicChunks.LOGGER.error("Not writing worldgen settings into existing world {}", worldDir);
            return;
        }

        try {
            cc$worldgenConfig.save(worldDir);
        } catch (ConfigurateException e) {
            // Creating the world without the settings the player picked would generate the wrong terrain
            CubicChunks.LOGGER.error("Could not write worldgen settings for new world {}", worldDir, e);
            mc.displayGuiScreen(
                new GuiErrorScreen(I18n.format("cubicchunks.wgconfig.save_failed"), String.valueOf(e.getMessage())));
            ci.cancel();
        }
    }
}
