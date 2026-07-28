package com.eaglerhub.web;

import com.eaglerhub.injector.JarLoader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
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
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Load jars so we can serve resources from them
        JarLoader jarLoader = new JarLoader(new java.io.File("chrome"));
        URLClassLoader loader = jarLoader.createCombinedLoader();

        server.createContext("/custom", new CustomResourceHandler(loader, new java.io.File("custom")));
        server.createContext("/admin", new AdminHandler(jarLoader));
        server.createContext("/api/mods", new ModsApiHandler(jarLoader));
        server.createContext("/launch", new LaunchHandler());

        server.setExecutor(null);
        server.start();
        log.info("WebServer started on port " + port);
        log.info("Serving /custom from folder './custom' and from chrome jars (" + jarLoader.getLoadedJarCount() + " jars)");
    }

    static class AdminHandler implements HttpHandler {
        private final JarLoader jarLoader;
        AdminHandler(JarLoader jl) { this.jarLoader = jl; }
        @Override public void handle(HttpExchange exchange) throws IOException {
            byte[] data = AdminPage.HTML.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();
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
                m.put("path", "/mods/js/" + f.getName());
                list.add(m);
            }
            for (java.net.URL u : jarLoader.getJarUrls()) {
                Map<String,Object> m = new HashMap<>();
                m.put("name", new java.io.File(u.getPath()).getName());
                m.put("type", "jar");
                m.put("path", "/mods/jar/" + new java.io.File(u.getPath()).getName());
                list.add(m);
            }
            String json = toJson(list);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();
        }
        private String toJson(Object o) {
            // very small JSON serializer for lists/maps
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

    static class LaunchHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            // simple launch page: reads ?mods=mod1.js,mod2.js
            String query = exchange.getRequestURI().getQuery();
            String modsParam = "";
            if (query != null) {
                for (String part : query.split("&")) if (part.startsWith("mods=")) { modsParam = part.substring(5); break; }
            }
            String[] mods = modsParam.isEmpty() ? new String[0] : modsParam.split(",");
            StringBuilder sb = new StringBuilder();
            sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>Launch Eaglercraft</title></head><body>\n");
            // include selected js mods
            for (String m : mods) {
                String nm = m.trim(); if (nm.isEmpty()) continue;
                sb.append("<script src=\"/mods/js/" + escapeHtml(nm) + "\"></script>\n");
            }
            // Then redirect or include the client. We'll open Eaglerclient (assuming /eaglerclient)
            sb.append("<script>window.location.href='/eaglerclient/';</script>");
            sb.append("</body></html>");
            byte[] data = sb.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();
        }
        private String escapeHtml(String s) { return s.replace("<","%3C").replace(">","%3E").replace("\"","%22"); }
    }
}
