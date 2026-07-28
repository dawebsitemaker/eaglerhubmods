package com.eaglerhub.web;

import com.eaglerhub.injector.JarLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class WebServer {
    private static final Logger log = Logger.getLogger("WebServer");

    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new java.net.InetSocketAddress(port), 0);

        // Load jars so we can serve resources from them
        JarLoader jarLoader = new JarLoader(new java.io.File("chrome"));

        server.createContext("/custom", new CustomResourceHandler(null, new java.io.File("custom")));
        server.createContext("/admin", new AdminHandler());
        server.createContext("/api/mods", new ModsApiHandler(jarLoader));
        server.createContext("/mods/js", new ModsJsHandler(new File("chrome")));
        server.createContext("/mods/jar", new ModsJarHandler(new File("chrome")));
        server.createContext("/api/metrics", new MetricsHandler(new File("runtime/metrics.json")));
        server.createContext("/launch", new LaunchHandler());

        server.setExecutor(null);
        server.start();
        log.info("WebServer started on port " + port);
        log.info("Serving /custom from folder './custom' and exposing mods from chrome/ (" + jarLoader.getLoadedJarCount() + " jars)");
    }

    static class AdminHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            // serve admin.html from resources
            try (InputStream in = WebServer.class.getResourceAsStream("/admin.html")) {
                if (in == null) { exchange.sendResponseHeaders(404, -1); return; }
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, 0);
                try (OutputStream os = exchange.getResponseBody()) { in.transferTo(os); }
            }
        }
    }

    static class ModsApiHandler implements HttpHandler {
        private final JarLoader jarLoader;
        ModsApiHandler(JarLoader jl) { this.jarLoader = jl; }
        @Override public void handle(HttpExchange exchange) throws IOException {
            List<Map<String,Object>> list = new ArrayList<>();
            for (java.io.File f : jarLoader.getJsFiles()) {
                Map<String,Object> m = new HashMap<>();
                m.put("name", f.getName());
                m.put("type", "js");
                m.put("path", "/mods/js/" + URLEncoder.encode(f.getName(), StandardCharsets.UTF_8));
                list.add(m);
            }
            for (java.net.URL u : jarLoader.getJarUrls()) {
                String fname;
                try { fname = Path.of(u.toURI()).getFileName().toString(); } catch (Exception ex) { fname = new File(u.getPath()).getName(); }
                Map<String,Object> m = new HashMap<>();
                m.put("name", fname);
                m.put("type", "jar");
                m.put("path", "/mods/jar/" + URLEncoder.encode(fname, StandardCharsets.UTF_8));
                list.add(m);
            }
            String json = toJson(list);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(data); }
        }
        private String toJson(Object o) {
            if (o instanceof List) {
                StringBuilder sb = new StringBuilder(); sb.append('[');
                boolean first = true;
                for (Object e : (List<?>)o) { if (!first) sb.append(','); first=false; sb.append(toJson(e)); }
                sb.append(']'); return sb.toString();
            } else if (o instanceof Map) {
                StringBuilder sb = new StringBuilder(); sb.append('{'); boolean first=true;
                for (Map.Entry<?,?> en : ((Map<?,?>)o).entrySet()) {
                    if (!first) sb.append(','); first=false;
                    sb.append('"').append(escape(en.getKey().toString())).append('"').append(':');
                    Object v = en.getValue();
                    if (v instanceof Number) sb.append(v.toString()); else sb.append('"').append(escape(v.toString())).append('"');
                }
                sb.append('}'); return sb.toString();
            }
            return "null";
        }
        private String escape(String s) { return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n"); }
    }

    static class ModsJsHandler implements HttpHandler {
        private final File chromeDir;
        ModsJsHandler(File chromeDir) { this.chromeDir = chromeDir; }
        @Override public void handle(HttpExchange exchange) throws IOException {
            String raw = exchange.getRequestURI().getPath();
            String prefix = "/mods/js/";
            String rel = raw.startsWith(prefix) ? raw.substring(prefix.length()) : raw;
            rel = java.net.URLDecoder.decode(rel, StandardCharsets.UTF_8);
            if (rel.contains("..")) { exchange.sendResponseHeaders(403, -1); return; }
            File f = new File(chromeDir, rel);
            if (!f.exists() || !f.isFile()) { exchange.sendResponseHeaders(404, -1); return; }
            exchange.getResponseHeaders().add("Content-Type", "application/javascript; charset=utf-8");
            exchange.sendResponseHeaders(200, f.length());
            try (OutputStream os = exchange.getResponseBody(); InputStream in = Files.newInputStream(f.toPath())) { in.transferTo(os); }
        }
    }

    static class ModsJarHandler implements HttpHandler {
        private final File chromeDir;
        ModsJarHandler(File chromeDir) { this.chromeDir = chromeDir; }
        @Override public void handle(HttpExchange exchange) throws IOException {
            String raw = exchange.getRequestURI().getPath();
            String prefix = "/mods/jar/";
            String rel = raw.startsWith(prefix) ? raw.substring(prefix.length()) : raw;
            rel = java.net.URLDecoder.decode(rel, StandardCharsets.UTF_8);
            if (rel.contains("..")) { exchange.sendResponseHeaders(403, -1); return; }
            File f = new File(chromeDir, rel);
            if (!f.exists() || !f.isFile()) { exchange.sendResponseHeaders(404, -1); return; }
            exchange.getResponseHeaders().add("Content-Type", "application/java-archive");
            exchange.sendResponseHeaders(200, f.length());
            try (OutputStream os = exchange.getResponseBody(); InputStream in = Files.newInputStream(f.toPath())) { in.transferTo(os); }
        }
    }

    static class MetricsHandler implements HttpHandler {
        private final Path metricsFile;
        MetricsHandler(File metricsFile) { this.metricsFile = metricsFile.toPath(); }
        @Override public void handle(HttpExchange exchange) throws IOException {
            if (!Files.exists(metricsFile)) { exchange.sendResponseHeaders(204, -1); return; }
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            byte[] data = Files.readAllBytes(metricsFile);
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(data); }
        }
    }

    static class LaunchHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            // read mods param and build launch manifest for injector and html for client
            String query = exchange.getRequestURI().getQuery();
            String modsParam = "";
            if (query != null) {
                for (String part : query.split("&")) if (part.startsWith("mods=")) { modsParam = part.substring(5); break; }
            }
            String[] mods = modsParam.isEmpty() ? new String[0] : modsParam.split(",");

            // write manifest for injector to pick up
            try {
                Path outdir = Path.of("injector"); Files.createDirectories(outdir);
                StringBuilder sb = new StringBuilder(); sb.append('{'); sb.append("\"mods\":[");
                boolean first = true; for (String m : mods) { if (!first) sb.append(','); first=false; sb.append('"').append(escapeJson(m)).append('"'); }
                sb.append("],\"ts\":").append(System.currentTimeMillis()).append('}');
                Files.writeString(outdir.resolve("launch.json"), sb.toString(), StandardCharsets.UTF_8);
            } catch (Exception ex) { log.warning("Failed to write launch manifest: " + ex); }

            // try HTTP notify injector
            try {
                URL u = new URL("http://127.0.0.1:9091/launch");
                HttpURLConnection c = (HttpURLConnection)u.openConnection();
                c.setRequestMethod("POST"); c.setDoOutput(true);
                c.setRequestProperty("Content-Type","application/json; charset=utf-8");
                String body = "{" + "\"mods\":[]" + "}"; // minimal - we just notify
                try (OutputStream os = c.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
                int rc = c.getResponseCode();
                log.info("Injector notified via HTTP, rc=" + rc);
            } catch (Throwable t) { /* injector not running or refused */ }

            // build launch HTML that includes selected scripts before redirecting to client
            StringBuilder sb = new StringBuilder();
            sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>Launch Eaglercraft</title></head><body>\n");
            for (String m : mods) {
                String nm = m.trim(); if (nm.isEmpty()) continue;
                sb.append("<script src=\"/mods/js/" + URLEncoder.encode(nm, StandardCharsets.UTF_8) + "\"></script>\n");
            }
            sb.append("<script>setTimeout(function(){ window.location.href='/eaglerclient/'; }, 100);</script>");
            sb.append("</body></html>");
            byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(data); }
        }
        private String escapeJson(String s) { return s.replace("\\","\\\\").replace("\"","\\\""); }
    }
}
