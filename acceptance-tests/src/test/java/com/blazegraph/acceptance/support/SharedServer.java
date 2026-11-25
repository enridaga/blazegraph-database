package com.blazegraph.acceptance.support;

/**
 * A single Blazegraph server shared by all black-box HTTP tests of a test run.
 * It is started lazily on first use and stopped when the test JVM exits.
 * Tests isolate their data by creating their own namespaces
 * (see {@link Namespaces}).
 */
public final class SharedServer {

    private static BlazegraphServer server;

    private SharedServer() {
    }

    public static synchronized BlazegraphServer get() {
        if (server == null || !server.isAlive()) {
            server = BlazegraphServer.start("shared");
            Runtime.getRuntime().addShutdownHook(new Thread(server::close, "blazegraph-stop"));
        }
        return server;
    }
}
