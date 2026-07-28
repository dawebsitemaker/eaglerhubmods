package com.eaglerhub.web;

import com.eaglerhub.injector.JarLoader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLClassLoader;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpServer;

public class WebServer {
    private static final Logger log = Logger.getLogger("WebServer");

    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Load jars so we can serve resources from them
        JarLoader jarLoader = new JarLoader(new java.io.File("chrome"));
        URLClassLoader loader = jarLoader.createCombinedLoader();

        server.createContext("/custom", new CustomResourceHandler(loader, new java.io.File("custom")));

        server.setExecutor(null);
        server.start();
        log.info("WebServer started on port " + port);
        log.info("Serving /custom from folder './custom' and from chrome jars (" + jarLoader.getLoadedJarCount() + " jars)");
    }
}
