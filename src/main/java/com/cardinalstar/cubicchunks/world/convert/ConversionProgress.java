package com.cardinalstar.cubicchunks.world.convert;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

/**
 * Thread-safe progress state for a running world conversion.
 * Written by the worker thread; read by the GUI thread.
 */
public final class ConversionProgress {

    private final AtomicInteger completed = new AtomicInteger(0);
    private final AtomicInteger total     = new AtomicInteger(0);
    private final AtomicReference<String> dimName = new AtomicReference<>("");
    private final AtomicReference<String> status = new AtomicReference<>("");
    private final AtomicBoolean done    = new AtomicBoolean(false);
    private final AtomicBoolean errored = new AtomicBoolean(false);

    @Nullable
    private volatile String errorMessage;

    public void setDimension(String dimName) {
        this.dimName.set(dimName == null || dimName.isEmpty() ? "" : dimName + ": ");
    }

    public void update(int completedNow, int totalNow, String statusMessage) {
        this.total.set(totalNow);
        this.completed.set(completedNow);
        this.status.set(statusMessage);
    }

    public void markDone() {
        this.done.set(true);
    }

    public void markError(String message) {
        this.errorMessage = message;
        this.errored.set(true);
    }

    public int getCompleted() { return completed.get(); }

    public int getTotal() { return total.get(); }

    public String getStatus() { return dimName.get() + status.get(); }

    /** Progress as a fraction 0.0–1.0. Returns 0 if total is unknown. */
    public float getPercent() {
        int t = total.get();
        return t == 0 ? 0f : (float) completed.get() / t;
    }

    public boolean isDone()    { return done.get(); }

    public boolean isErrored() { return errored.get(); }

    @Nullable
    public String getErrorMessage() { return errorMessage; }
}
