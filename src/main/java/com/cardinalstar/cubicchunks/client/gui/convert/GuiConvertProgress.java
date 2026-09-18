package com.cardinalstar.cubicchunks.client.gui.convert;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import com.cardinalstar.cubicchunks.client.gui.convert.GuiConvertWorld.BlockFormat;
import com.cardinalstar.cubicchunks.client.gui.convert.GuiConvertWorld.ConversionTarget;
import com.cardinalstar.cubicchunks.world.convert.CCToVanillaConverter;
import com.cardinalstar.cubicchunks.world.convert.ConversionProgress;
import com.cardinalstar.cubicchunks.world.convert.IWorldConverter;
import com.cardinalstar.cubicchunks.world.convert.VanillaToCCConverter;
import com.cardinalstar.cubicchunks.world.convert.WorldSaveFormat;
import com.cardinalstar.cubicchunks.world.convert.adapter.SectionAdapterDiscovery;
import com.cardinalstar.cubicchunks.world.convert.adapter.SectionAdapter;
import com.cardinalstar.cubicchunks.world.convert.adapter.biome.BiomeAdapter;
import com.cardinalstar.cubicchunks.world.convert.adapter.biome.BiomeAdapterDiscovery;
import it.unimi.dsi.fastutil.Pair;

/**
 * Shows a progress bar while a world format conversion runs on a background thread.
 *
 * <p>Opened by {@link GuiConvertWorld} after the user confirms the backup warning.
 */
@ParametersAreNonnullByDefault
public class GuiConvertProgress extends GuiScreen {

    private static final int BTN_CANCEL = 0;

    private final GuiScreen parent;
    private final File worldDir;
    private final WorldSaveFormat sourceFormat;
    private final ConversionTarget targetFormat;
    private final BlockFormat blockFormat;

    private final ConversionProgress progress = new ConversionProgress();
    private final AtomicBoolean cancelSignal = new AtomicBoolean(false);

    private Thread workerThread;
    private boolean shownResult = false;

    public GuiConvertProgress(GuiScreen parent, File worldDir, WorldSaveFormat sourceFormat,
                               ConversionTarget targetFormat, BlockFormat blockFormat
    ) {
        this.parent = parent;
        this.worldDir = worldDir;
        this.sourceFormat = sourceFormat;
        this.targetFormat = targetFormat;
        this.blockFormat = blockFormat;
    }

    private IWorldConverter buildConverter(ConversionTarget target) {
        SectionAdapter sectionWriteAdapter = blockFormat == BlockFormat.EID
            ? SectionAdapterDiscovery.eid()
            : SectionAdapterDiscovery.vanilla();

        BiomeAdapter biomeWriteAdapter = blockFormat == BlockFormat.EID
            ? BiomeAdapterDiscovery.eid()
            : BiomeAdapterDiscovery.vanilla();

        if (target == ConversionTarget.VANILLA) {
            return new CCToVanillaConverter(sectionWriteAdapter, biomeWriteAdapter);
        } else {
            return new VanillaToCCConverter(sectionWriteAdapter, biomeWriteAdapter);
        }
    }

    private static final String[] DIM_PREFIXES = {
        "DIM",
        "PERSONAL_DIM"
    };

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(new GuiButton(BTN_CANCEL, width / 2 - 75, height - 38, 150, 20,
            I18n.format("cubicchunks.gui.progress.cancel")));

        workerThread = new Thread(() -> {
            List<Pair<Path, String>> dims = new ArrayList<>();

            dims.add(Pair.of(worldDir.toPath(), "Overworld"));

            try(Stream<Path> stream = Files.list(worldDir.toPath())) {
                stream.filter(Files::isDirectory)
                    .filter(p -> {
                        var name = p.getFileName().toString();

                        for (var prefix : DIM_PREFIXES) {
                            if (name.startsWith(prefix)) return true;
                        }

                        return false;
                    })
                    .forEach(p -> {
                        dims.add(Pair.of(p, p.getFileName().toString()));
                    });
            } catch (IOException e) {
                throw new RuntimeException("Could not find dimension folders", e);
            }

            try {
                for (var dim : dims) {
                    progress.setDimension(dim.right());

                    buildConverter(targetFormat).convert(dim.left().toFile(), dim.right().equals("Overworld"), progress, cancelSignal);
                }

                progress.markDone();
            } catch (IOException e) {
                progress.markError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        }, "CC-WorldConverter");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @Override
    public void updateScreen() {
        if (shownResult) return;

        if (progress.isDone()) {
            shownResult = true;
            mc.displayGuiScreen(makeResultScreen(
                I18n.format("cubicchunks.gui.progress.title"),
                I18n.format("cubicchunks.gui.progress.done")));
        } else if (progress.isErrored()) {
            shownResult = true;
            String msg = progress.getErrorMessage();
            mc.displayGuiScreen(makeResultScreen(
                I18n.format("cubicchunks.gui.progress.title"),
                I18n.format("cubicchunks.gui.progress.error", msg != null ? msg : "unknown")));
        }
    }

    private GuiScreen makeResultScreen(String title, String message) {
        GuiScreen returnTo = parent;
        return new GuiScreen() {
            @Override
            public void initGui() {
                buttonList.add(new GuiButton(0, width / 2 - 75, height - 38, 150, 20,
                    I18n.format("gui.done")));
            }

            @Override
            protected void actionPerformed(GuiButton button) {
                mc.displayGuiScreen(returnTo);
            }

            @Override
            public void drawScreen(int mouseX, int mouseY, float partialTicks) {
                drawDefaultBackground();
                drawCenteredString(fontRendererObj, title,   width / 2, height / 2 - 16, 0xFFFFFF);
                drawCenteredString(fontRendererObj, message, width / 2, height / 2,      0xAAAAAA);
                super.drawScreen(mouseX, mouseY, partialTicks);
            }
        };
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BTN_CANCEL) {
            cancelSignal.set(true);
            shownResult = true;
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        String title = I18n.format("cubicchunks.gui.progress.title");
        drawCenteredString(fontRendererObj, title, width / 2, 20, 0xFFFFFF);

        int barX = width / 2 - 100;
        int barY = height / 2 - 10;
        int barW = 200;
        int barH = 10;

        drawRect(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF555555);
        drawRect(barX, barY, barX + (int) (barW * progress.getPercent()), barY + barH, 0xFF55FF55);

        String status = progress.getStatus();
        if (!status.isEmpty()) {
            drawCenteredString(fontRendererObj, status, width / 2, barY + barH + 6, 0xAAAAAA);
        }

        int completed = progress.getCompleted();
        int total     = progress.getTotal();
        if (total > 0) {
            String countStr = I18n.format("cubicchunks.gui.progress.status", completed, total);
            drawCenteredString(fontRendererObj, countStr, width / 2, barY - 14, 0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
