package com.eaglerhub.injector;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class PerfController {
    public enum Profile {LOW, BALANCED, HIGH, AUTO}

    private final CopyOnWriteArrayList<PerfListener> listeners = new CopyOnWriteArrayList<>();
    private final MovingAverage fpsAvg = new MovingAverage(100);
    private final AtomicReference<Profile> profile = new AtomicReference<>(Profile.BALANCED);
    private final Logger log = Logger.getLogger("PerfController");
    private final Path runtimeDir;

    public PerfController() { this(new File("runtime")); }
    public PerfController(File runtimeDir) {
        this.runtimeDir = runtimeDir.toPath();
        try { Files.createDirectories(this.runtimeDir); } catch (IOException e) { /* ignore */ }
        // monitor thread to adjust profile when AUTO
        Thread t = new Thread(this::monitorLoop, "PerfController-Monitor");
        t.setDaemon(true);
        t.start();
        // writer thread to emit metrics periodically
        Thread writer = new Thread(this::metricsWriterLoop, "PerfController-Writer");
        writer.setDaemon(true);
        writer.start();
    }

    public void registerFrame(long frameTimeNanos) {
        if (frameTimeNanos <= 0) return;
        double fps = 1_000_000_000.0 / frameTimeNanos;
        fpsAvg.add(fps);
    }

    public double getFps() { return fpsAvg.get(); }

    public Profile getProfile() { return profile.get(); }

    public void setProfile(Profile p) {
        profile.set(p);
        writeProfileFile(p);
        for (PerfListener l : listeners) {
            try { l.onProfileChange(p); } catch (Throwable t) { log.warning("Listener error: " + t); }
        }
    }

    public void addListener(PerfListener l) { listeners.addIfAbsent(l); }
    public void removeListener(PerfListener l) { listeners.remove(l); }

    private void monitorLoop() {
        try {
            while (true) {
                Thread.sleep(1000);
                if (getProfile() == Profile.AUTO) {
                    double avg = getFps();
                    // simple hysteresis
                    if (avg < 18) setProfile(Profile.LOW);
                    else if (avg < 25) setProfile(Profile.BALANCED);
                    else setProfile(Profile.HIGH);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void metricsWriterLoop() {
        try {
            while (true) {
                try {
                    writeMetricsFile();
                } catch (Throwable t) {
                    log.warning("Failed to write metrics: " + t);
                }
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeMetricsFile() {
        try {
            String json = String.format("{\"fps\":%.2f,\"profile\":\"%s\"}", getFps(), getProfile().name());
            Path p = runtimeDir.resolve("metrics.json");
            try (FileWriter w = new FileWriter(p.toFile(), StandardCharsets.UTF_8, false)) {
                w.write(json);
            }
        } catch (IOException e) {
            log.warning("Unable to write metrics.json: " + e);
        }
    }

    private void writeProfileFile(Profile p) {
        try {
            String json = String.format("{\"profile\":\"%s\"}", p.name());
            Path pth = runtimeDir.resolve("profile.json");
            try (FileWriter w = new FileWriter(pth.toFile(), StandardCharsets.UTF_8, false)) {
                w.write(json);
            }
        } catch (IOException e) {
            log.warning("Unable to write profile.json: " + e);
        }
    }

    public interface PerfListener { void onProfileChange(Profile p); }
}
