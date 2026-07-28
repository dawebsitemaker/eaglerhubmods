package com.eaglerhub.injector;

import java.io.File;
import java.net.URLClassLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InjectorMain {
    private static final Logger log = Logger.getLogger("InjectorMain");

    public static void main(String[] args) {
        try {
            File chromeDir = new File("chrome");
            JarLoader jarLoader = new JarLoader(chromeDir);
            URLClassLoader loader = jarLoader.createCombinedLoader();

            // Set context classloader so other code can find classes/resources
            Thread.currentThread().setContextClassLoader(loader);

            log.info("Loaded chrome jars: " + jarLoader.getLoadedJarCount());
            log.info("Injector is running. Context classloader updated.");

            // Optionally, run plugin init hook if available
            // For now, just keep the process running
            Thread.sleep(1000);
        } catch (Throwable t) {
            log.log(Level.SEVERE, "Fatal in injector", t);
            System.exit(1);
        }
    }
}
