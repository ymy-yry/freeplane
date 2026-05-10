package org.freeplane.plugin.ai.restapi;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/**
 * CORS utility class.
 * The browser's same-origin policy blocks the Vue frontend (localhost:5173) from accessing the
 * backend (localhost:6299). Adding CORS response headers tells the browser to allow cross-origin
 * requests.
 */
public class CorsFilter {

    /**
     * Adds CORS response headers to every response, allowing requests from any origin.
     */
    public static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
    }

    /**
     * Handles the browser's OPTIONS preflight request.
     * Before sending the actual request, the browser sends an OPTIONS request to ask whether
     * cross-origin access is permitted.
     *
     * @return {@code true} if this was an OPTIONS request (already handled); the caller should return immediately.
     */
    public static boolean handlePreflight(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            addCorsHeaders(exchange);
            exchange.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }
}
