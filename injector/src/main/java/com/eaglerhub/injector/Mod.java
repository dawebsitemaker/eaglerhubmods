package com.eaglerhub.injector;

public interface Mod {
    /** Called once when the mod is loaded. Implementations should be robust to multiple calls. */
    void init(PerfController perf);
}
