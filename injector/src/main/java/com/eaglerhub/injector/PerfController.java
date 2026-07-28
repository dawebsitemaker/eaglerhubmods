package com.eaglerhub.injector;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class PerfController {
    public enum Profile {LOW, BALANCED, HIGH, AUTO}

    private final CopyOnWriteArrayList<PerfListener> listeners = new CopyOnWriteArrayList<>();
    private final MovingAverage fpsAvg = new MovingAverage(100);
    private final AtomicReference<Profile> profile = new AtomicReference<>(Profile.BALANCED);
    private final Logger log = Logger.getLogger("PerfController");

    public PerfController() {
        // monitor thread to adjust profile when AUTO
        Thread t = new Thread(this::monitorLoop, "PerfController-Monitor");
        t.setDaemon(true);
        t.start();
    }

    public void registerFrame(long frameTimeNanos) {
        if (frameTimeNanos <= 0) return;
        double fps = 1_000_000_000.0 / frameTimeNanos;
        fpsAvg.add(fps);
    }

    public double getFps() {
        return fpsAvg.get();
    }

    public Profile getProfile() {
        return profile.get();
    }

    public void setProfile(Profile p) {
        profile.set(p);
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

    public interface PerfListener { void onProfileChange(Profile p); }
}
