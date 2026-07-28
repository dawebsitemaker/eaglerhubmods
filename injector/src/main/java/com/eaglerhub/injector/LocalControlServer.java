package com.eaglerhub.injector;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * A tiny localhost-only control server that accepts POST /launch and logs/records the payload.
 * This allows the webserver to notify the injector when a launch is requested.
 */
public class LocalControlServer {
    private final HttpServer server;
    private final Logger log = Logger.getLogger("LocalControlServer");

    public LocalControlServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/launch", new LaunchHandler());
        server.setExecutor(null);
        server.start();
        log.info("LocalControlServer started on 127.0.0.1:" + port);
    }

    public void stop() { server.stop(0); }

    class LaunchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
            }
            String body = sb.toString();
            log.info("Received launch payload via HTTP control: " + body.replaceAll("\n","\\n"));
            // acknowledge
            Headers h = exchange.getResponseHeaders();
            h.add("Content-Type", "application/json; charset=utf-8");
            byte[] resp = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(resp); }
        }
    }
}
