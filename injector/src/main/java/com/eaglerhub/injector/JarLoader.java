package com.eaglerhub.injector;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class JarLoader {
    private final File dir;
    private final List<URL> jarUrls = new ArrayList<>();
    private final List<File> jsFiles = new ArrayList<>();
    private final Logger log = Logger.getLogger("JarLoader");

    public JarLoader(File dir) {
        this.dir = dir;
        discover();
    }

    private void discover() {
        if (dir == null) return;
        if (!dir.exists()) {
            log.info("chrome directory does not exist: " + dir.getAbsolutePath());
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            try {
                if (f.isFile()) {
                    String name = f.getName().toLowerCase();
                    if (name.endsWith(".jar")) {
                        try {
                            URL u = f.toURI().toURL();
                            jarUrls.add(u);
                            log.info("Found jar: " + f.getName());
                        } catch (MalformedURLException e) {
                            log.warning("Skipping jar (bad url): " + f.getAbsolutePath());
                        }
                    } else if (name.endsWith(".js")) {
                        jsFiles.add(f);
                        log.info("Found js mod: " + f.getName());
                    }
                }
            } catch (Throwable t) {
                log.warning("Error inspecting file " + f + ": " + t);
            }
        }
    }

    public URLClassLoader createCombinedLoader() {
        if (jarUrls.isEmpty()) {
            return new URLClassLoader(new URL[0], Thread.currentThread().getContextClassLoader());
        }
        URL[] urls = jarUrls.toArray(new URL[0]);
        return new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
    }

    public List<URL> getJarUrls() { return Collections.unmodifiableList(jarUrls); }
    public List<File> getJsFiles() { return Collections.unmodifiableList(jsFiles); }
    public int getLoadedJarCount() { return jarUrls.size(); }
}
