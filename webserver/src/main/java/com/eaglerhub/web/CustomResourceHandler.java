package com.eaglerhub.web;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.logging.Logger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class CustomResourceHandler implements HttpHandler {
    private final URLClassLoader jarLoader;
    private final File customDir;
    private final Logger log = Logger.getLogger("CustomResourceHandler");

    public CustomResourceHandler(URLClassLoader jarLoader, File customDir) {
        this.jarLoader = jarLoader;
        this.customDir = customDir;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String raw = exchange.getRequestURI().getPath();
        String prefix = "/custom";
        String rel = raw.startsWith(prefix) ? raw.substring(prefix.length()) : raw;
        // normalize
        rel = rel.replaceAll("^/+", "");
        if (rel.contains("..")) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }

        // 1) Try file system
        File f = new File(customDir, rel);
        if (f.exists() && f.isFile()) {
            byte[] data = Files.readAllBytes(f.toPath());
            Headers h = exchange.getResponseHeaders();
            h.set("Content-Type", guessContentType(f.getName()));
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
            return;
        }

        // 2) Try loaded jars
        if (jarLoader != null) {
            String entryName = rel;
            // Try direct path first
            URL res = jarLoader.getResource(entryName);
            if (res == null) {
                // Try assets/ prefix (common in jars)
                res = jarLoader.getResource("assets/" + entryName);
            }
            if (res != null) {
                try (InputStream in = res.openStream()) {
                    byte[] data = in.readAllBytes();
                    Headers h = exchange.getResponseHeaders();
                    h.set("Content-Type", guessContentType(entryName));
                    exchange.sendResponseHeaders(200, data.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(data);
                    }
                    return;
                }
            }
        }

        exchange.sendResponseHeaders(404, -1);
    }

    private String guessContentType(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".js")) return "application/javascript";
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html";
        return "application/octet-stream";
    }
}
