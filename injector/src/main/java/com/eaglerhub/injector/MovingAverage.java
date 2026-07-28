package com.eaglerhub.injector;

import java.util.concurrent.atomic.AtomicLong;

class MovingAverage {
    private final int max;
    private final double[] buf;
    private int idx = 0;
    private int filled = 0;
    private double sum = 0.0;

    MovingAverage(int max) {
        this.max = Math.max(1, max);
        this.buf = new double[this.max];
    }

    synchronized void add(double v) {
        if (filled < max) {
            buf[idx++] = v; sum += v; filled++;
            if (idx >= max) idx = 0;
        } else {
            sum -= buf[idx];
            buf[idx] = v;
            sum += v;
            idx = (idx + 1) % max;
        }
    }

    synchronized double get() {
        return filled == 0 ? 0.0 : sum / filled;
    }
}
