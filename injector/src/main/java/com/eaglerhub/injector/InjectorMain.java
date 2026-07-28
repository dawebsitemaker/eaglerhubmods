package com.eaglerhub.injector;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InjectorMain {
    private static final Logger log = Logger.getLogger("InjectorMain");

    public static void main(String[] args) {
        try {
            File chromeDir = new File("chrome");
            JarLoader jarLoader = new JarLoader(chromeDir);
            URLClassLoader loader = jarLoader.createCombinedLoader();

            // Perf controller
            PerfController perf = new PerfController();

            // Set context classloader so other code can find classes/resources
            Thread.currentThread().setContextClassLoader(loader);

            // Load Java mods via ServiceLoader (mods must provide META-INF/services/...)
            ServiceLoader<Mod> mods = ServiceLoader.load(Mod.class, loader);
            int count = 0;
            for (Mod m : mods) {
                try {
                    m.init(perf);
                    count++;
                    log.info("Initialized mod: " + m.getClass().getName());
                } catch (Throwable t) {
                    log.log(Level.WARNING, "Mod init failed: " + m.getClass().getName(), t);
                }
            }

            log.info("Loaded chrome jars: " + jarLoader.getLoadedJarCount() + ", initialized mods: " + count);
            log.info("JS mods available: " + jarLoader.getJsFiles().size());

            // Keep process alive
            while (true) Thread.sleep(1000);
        } catch (Throwable t) {
            log.log(Level.SEVERE, "Fatal in injector", t);
            System.exit(1);
        }
    }
}
