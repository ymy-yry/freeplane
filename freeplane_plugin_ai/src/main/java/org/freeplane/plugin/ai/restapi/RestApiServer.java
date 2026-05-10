package org.freeplane.plugin.ai.restapi;

import com.sun.net.httpserver.HttpServer;
import org.freeplane.core.util.LogUtils;
import org.freeplane.plugin.ai.maps.AvailableMaps;
import org.freeplane.plugin.ai.chat.AIChatPanel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Lightweight REST API HTTP server listening on port 6299.
 * Built on the JDK built-in {@code com.sun.net.httpserver.HttpServer} with no additional dependencies.
 * Runs in parallel with the existing MCP Server (port 6298) without interference.
 */
public class RestApiServer {

    public static final int PORT = 6299;

    private final HttpServer server;

    public RestApiServer(AvailableMaps availableMaps, AIChatPanel aiChatPanel) throws IOException {
        server = HttpServer.create(new InetSocketAddress(PORT), 0);
        RestApiRouter router = new RestApiRouter(availableMaps, aiChatPanel);
        router.registerAll(server);
        server.setExecutor(Executors.newFixedThreadPool(4));
    }

    public void start() {
        server.start();
        LogUtils.info("RestApiServer: started on port " + PORT);
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            LogUtils.info("RestApiServer: stopped");
        }
    }
}
