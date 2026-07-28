package com.eaglerhub.injector;

import java.io.File;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class JarLoader {
    private final File dir;
    private final List<URL> jarUrls = new ArrayList<>();
    private final Logger log = Logger.getLogger("JarLoader");

    public JarLoader(File dir) {
        this.dir = dir;
        discoverJars();
    }

    private void discoverJars() {
        if (dir == null) return;
        if (!dir.exists()) {
            log.info("chrome directory does not exist: " + dir.getAbsolutePath());
            return;
        }
        File[] jars = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isFile() && pathname.getName().toLowerCase().endsWith(".jar");
            }
        });
        if (jars == null) return;
        for (File f : jars) {
            try {
                URL u = f.toURI().toURL();
                jarUrls.add(u);
                log.info("Found jar: " + f.getName());
            } catch (MalformedURLException e) {
                log.warning("Skipping jar (bad url): " + f.getAbsolutePath());
            }
        }
    }

    public URLClassLoader createCombinedLoader() {
        URL[] urls = jarUrls.toArray(new URL[0]);
        return new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
    }

    public int getLoadedJarCount() {
        return jarUrls.size();
    }

    public List<URL> getJarUrls() {
        return new ArrayList<>(jarUrls);
    }
}
