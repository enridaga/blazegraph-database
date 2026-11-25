package com.blazegraph.acceptance.support;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Launches the Blazegraph executable jar in a separate JVM and waits until
 * its HTTP endpoint is ready.
 * <p>
 * Configuration (JVM system properties):
 * <ul>
 * <li>{@code blazegraph.jar} - path of the executable jar under test (required)</li>
 * <li>{@code blazegraph.java} - java executable used to run it (optional,
 * defaults to the JVM running the tests)</li>
 * <li>{@code blazegraph.workdir} - parent directory for server working
 * directories (optional)</li>
 * </ul>
 */
public final class BlazegraphServer implements AutoCloseable {

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(180);

    private final Path workDir;
    private final List<String> jvmArgs;
    private final int port;
    private Process process;

    private BlazegraphServer(Path workDir, int port, List<String> jvmArgs) {
        this.workDir = workDir;
        this.port = port;
        this.jvmArgs = List.copyOf(jvmArgs);
    }

    /** Starts a server in a fresh working directory. */
    public static BlazegraphServer start(String name, String... jvmArgs) {
        return start(newWorkDir(name), jvmArgs);
    }

    /**
     * Starts a server in the given working directory. The journal file
     * ({@code blazegraph.jnl}) lives in that directory, so restarting on the
     * same directory reopens the same database.
     */
    public static BlazegraphServer start(Path workDir, String... jvmArgs) {
        BlazegraphServer s = new BlazegraphServer(workDir, freePort(), List.of(jvmArgs));
        s.startProcess();
        return s;
    }

    public static Path newWorkDir(String name) {
        try {
            String parent = System.getProperty("blazegraph.workdir");
            Path base = parent == null || parent.isBlank()
                    ? Paths.get(System.getProperty("java.io.tmpdir"))
                    : Paths.get(parent);
            Files.createDirectories(base);
            return Files.createTempDirectory(base, name + "-");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Path jar() {
        String p = System.getProperty("blazegraph.jar");
        if (p == null || p.isBlank()) {
            throw new IllegalStateException("System property blazegraph.jar is not set");
        }
        Path jar = Paths.get(p).toAbsolutePath().normalize();
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Blazegraph jar not found: " + jar
                    + " (build it with 'mvn -P Deployment install' or pass -Dblazegraph.jar=...)");
        }
        return jar;
    }

    private static String javaExecutable() {
        String p = System.getProperty("blazegraph.java");
        if (p != null && !p.isBlank()) {
            return p;
        }
        return Paths.get(System.getProperty("java.home"), "bin", "java").toString();
    }

    private void startProcess() {
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExecutable());
        cmd.add("-server");
        cmd.add("-Xmx1g");
        cmd.add("-Djetty.port=" + port);
        cmd.addAll(jvmArgs);
        cmd.add("-jar");
        cmd.add(jar().toString());
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile().toFile()));
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot launch " + cmd, e);
        }
        awaitReady();
    }

    private void awaitReady() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("Blazegraph exited with code "
                        + process.exitValue() + "\n" + logTail());
            }
            try {
                HttpResponse<Void> r = client.send(
                        HttpRequest.newBuilder(URI.create(baseUrl() + "/status"))
                                .timeout(Duration.ofSeconds(5)).GET().build(),
                        HttpResponse.BodyHandlers.discarding());
                if (r.statusCode() == 200) {
                    return;
                }
            } catch (IOException e) {
                // not yet listening
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            sleep(500);
        }
        close();
        throw new IllegalStateException("Blazegraph did not start within " + STARTUP_TIMEOUT + "\n" + logTail());
    }

    /** {@code http://localhost:PORT} */
    public String rootUrl() {
        return "http://localhost:" + port;
    }

    /** {@code http://localhost:PORT/blazegraph} */
    public String baseUrl() {
        return rootUrl() + "/blazegraph";
    }

    /** SPARQL endpoint of the default namespace. */
    public String sparqlUrl() {
        return baseUrl() + "/sparql";
    }

    /** SPARQL endpoint of the given namespace. */
    public String sparqlUrl(String namespace) {
        return baseUrl() + "/namespace/" + namespace + "/sparql";
    }

    public int port() {
        return port;
    }

    public Path workDir() {
        return workDir;
    }

    public Path logFile() {
        return workDir.resolve("server.log");
    }

    public String logTail() {
        try {
            List<String> lines = Files.readAllLines(logFile());
            return String.join("\n", lines.subList(Math.max(0, lines.size() - 60), lines.size()));
        } catch (IOException e) {
            return "(no log: " + e + ")";
        }
    }

    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    /** Stops the server gracefully (SIGTERM, Jetty stopAtShutdown), then forcibly. */
    @Override
    public void close() {
        if (process == null) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor(30, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        process = null;
    }

    private static int freePort() {
        try (ServerSocket s = new ServerSocket(0)) {
            s.setReuseAddress(true);
            return s.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
