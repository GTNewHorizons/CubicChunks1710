package com.cardinalstar.cubicchunks.client.gui.convert;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.ParametersAreNonnullByDefault;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import com.cardinalstar.cubicchunks.CubicChunks;
import com.cardinalstar.cubicchunks.client.gui.convert.GuiConvertWorld.ConversionTarget;
import com.cardinalstar.cubicchunks.world.convert.CCToVanillaConverter;
import com.cardinalstar.cubicchunks.world.convert.ConversionInstall;
import com.cardinalstar.cubicchunks.world.convert.ConversionProgress;
import com.cardinalstar.cubicchunks.world.convert.ConversionReport;
import com.cardinalstar.cubicchunks.world.convert.VanillaToCCConverter;
import com.cardinalstar.cubicchunks.world.convert.WorldConverter;

/** Shows a progress bar while a world conversion runs on a background thread, then what it did. */
@ParametersAreNonnullByDefault
public class GuiConvertProgress extends GuiScreen {

    private static final int BTN_CANCEL = 0;

    private final GuiScreen parent;
    private final File worldDir;
    private final ConversionTarget targetFormat;

    private final ConversionProgress progress = new ConversionProgress();
    private final AtomicBoolean cancelSignal = new AtomicBoolean(false);
    private volatile ConversionReport report;

    private Thread workerThread;
    private boolean shownResult = false;

    public GuiConvertProgress(GuiScreen parent, File worldDir, ConversionTarget targetFormat) {
        this.parent = parent;
        this.worldDir = worldDir;
        this.targetFormat = targetFormat;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonList.add(
            new GuiButton(
                BTN_CANCEL,
                width / 2 - 75,
                height - 38,
                150,
                20,
                I18n.format("cubicchunks.gui.progress.cancel")));
        buttonList.get(0).enabled = !cancelSignal.get();

        if (workerThread != null) return;

        workerThread = new Thread(() -> {
            try {
                WorldConverter converter = targetFormat == ConversionTarget.VANILLA ? new CCToVanillaConverter()
                    : new VanillaToCCConverter();
                report = converter.convertWorld(worldDir, progress, cancelSignal);
                progress.markDone();
            } catch (InterruptedIOException e) {
                if (cancelSignal.get()) progress.markCancelled();
                else progress.markError(e.getMessage());
            } catch (IOException | RuntimeException | Error e) {
                CubicChunks.LOGGER.error("World conversion of {} failed", worldDir, e);
                progress.markError(
                    e.getMessage() != null ? e.getMessage()
                        : e.getClass()
                            .getSimpleName());
                // Let fatal errors escape, but do not leave the GUI waiting for a dead worker.
                if (e instanceof Error) throw (Error) e;
            }
        }, "CC-WorldConverter");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @Override
    public void updateScreen() {
        if (shownResult) return;

        if (progress.isCancelled()) {
            shownResult = true;
            mc.displayGuiScreen(parent);
        } else if (progress.isDone()) {
            shownResult = true;
            mc.displayGuiScreen(makeResultScreen(I18n.format("cubicchunks.gui.progress.done"), resultLines(report)));
        } else if (progress.isErrored()) {
            shownResult = true;
            String msg = progress.getErrorMessage();
            List<String> lines = new ArrayList<>();
            lines.add(I18n.format("cubicchunks.gui.progress.error", msg != null ? msg : "unknown"));
            // A failed install that could not be taken back is finished when the world is next listed or opened.
            lines.add(
                I18n.format(
                    ConversionInstall.isPending(worldDir.toPath()) ? "cubicchunks.gui.progress.pending"
                        : "cubicchunks.gui.progress.unchanged"));
            mc.displayGuiScreen(makeResultScreen(I18n.format("cubicchunks.gui.progress.failed"), lines));
        }
    }

    private List<String> resultLines(ConversionReport report) {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.format("cubicchunks.gui.result.converted", report.chunks.get(), report.dimensions.get()));
        if (report.unreadableChunks.get() > 0) {
            lines.add(I18n.format("cubicchunks.gui.result.unreadable", report.unreadableChunks.get()));
        }
        if (report.droppedOutOfRange.get() > 0) {
            lines.add(I18n.format("cubicchunks.gui.result.dropped", report.droppedOutOfRange.get()));
        }
        if (report.chunksLeftToGame.get() > 0) {
            lines.add(I18n.format("cubicchunks.gui.result.left_to_game", report.chunksLeftToGame.get()));
        }
        if (report.incompleteChunks.get() > 0) {
            lines.add(I18n.format("cubicchunks.gui.result.incomplete", report.incompleteChunks.get()));
        }
        if (report.backup != null) {
            lines.add(
                I18n.format(
                    "cubicchunks.gui.result.backup",
                    // the backup folder inside the world folder: .cubicchunks-backup/<time>-<kind>
                    report.backup.getParent()
                        .getParent()
                        .relativize(report.backup)
                        .toString()));
        }
        return lines;
    }

    private GuiScreen makeResultScreen(String title, List<String> lines) {
        GuiScreen returnTo = parent;
        return new GuiScreen() {

            @Override
            public void initGui() {
                buttonList.clear();
                buttonList.add(new GuiButton(0, width / 2 - 75, height - 38, 150, 20, I18n.format("gui.done")));
            }

            @Override
            protected void actionPerformed(GuiButton button) {
                mc.displayGuiScreen(returnTo);
            }

            @Override
            public void drawScreen(int mouseX, int mouseY, float partialTicks) {
                drawDefaultBackground();
                drawCenteredString(fontRendererObj, title, width / 2, 20, 0xFFFFFF);
                GuiConvertWorld.drawParagraphs(this, lines, 44);
                super.drawScreen(mouseX, mouseY, partialTicks);
            }
        };
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BTN_CANCEL) {
            cancelSignal.set(true);
            button.enabled = false;
        }
    }

    @Override
    protected void keyTyped(char character, int keyCode) {
        if (keyCode == 1) actionPerformed(buttonList.get(0));
        else super.keyTyped(character, keyCode);
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
        drawRect(barX, barY, barX + (int) (barW * Math.min(1f, progress.getPercent())), barY + barH, 0xFF55FF55);

        String status = progress.getStatus();
        if (!status.isEmpty()) {
            drawCenteredString(fontRendererObj, status, width / 2, barY + barH + 6, 0xAAAAAA);
        }

        int completed = progress.getCompleted();
        int total = progress.getTotal();
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
