package com.gd.skoda;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * End-to-end smoke test of the packaged application as a stdio MCP server: starts
 * {@code java -jar target/skoda-mcp-server-*.jar} as a child process with a stub MyŠkoda Public API,
 * then talks MCP to it with the MCP Java SDK client.
 *
 * <p>A failsafe test ({@code *IT}) so it runs in {@code verify} against the repackaged jar. Every line the
 * server writes to stdout must be a JSON-RPC message: the SDK's stdio client stops reading at the first
 * line that is not, so a successful session also proves that logs go to stderr only.
 */
class McpStdioServerIT {

    private static final String VIN = "TMBJJ7NE8L0123456";
    private static final String API_KEY = "it-dummy-api-key";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private HttpServer upstream;
    private final List<String> upstreamRequests = Collections.synchronizedList(new ArrayList<>());
    private final List<String> receivedApiKeys = Collections.synchronizedList(new ArrayList<>());
    private final StringBuffer serverStderr = new StringBuffer();
    private McpSyncClient client;

    @BeforeEach
    void startUpstream() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        upstream.start();
    }

    @AfterEach
    void stop() {
        if (client != null) {
            client.closeGracefully();
        }
        upstream.stop(0);
    }

    @Test
    void servesToolsOverStdioInTheDefaultRunMode() {
        stubUpstream(200, "application/json", fixture("vehicle-full.json"), Map.of(
                "X-API-Key-Expires-At", "2026-12-31T00:00:00Z",
                "RateLimit-Limit", "20",
                "RateLimit-Remaining", "19",
                "RateLimit-Reset", "3600"));
        client = startServer(); // no profile argument: mcp is the default

        InitializeResult init = client.initialize();
        assertThat(init.serverInfo().name()).isEqualTo("skoda-mcp");
        assertThat(init.capabilities().tools()).isNotNull();
        assertThat(init.instructions()).contains("Read-only access to one Škoda vehicle");

        List<Tool> tools = client.listTools().tools();
        assertThat(tools).extracting(Tool::name).containsExactlyInAnyOrder("get_vehicle_overview",
                "get_vehicle_location", "get_vehicle_range", "get_vehicle_status", "get_api_key_status");
        assertThat(tools).allSatisfy(tool -> {
            assertThat(tool.description()).isNotBlank();
            assertThat(tool.outputSchema()).containsEntry("type", "object");
            assertThat(tool.annotations().readOnlyHint()).isTrue();
        });

        CallToolResult location = client.callTool(new CallToolRequest("get_vehicle_location", Map.of()));
        assertThat(location.isError()).isNotEqualTo(Boolean.TRUE);
        Map<String, Object> locationContent = structured(location);
        assertThat(map(locationContent.get("data")))
                .containsEntry("state", "PARKED")
                .containsEntry("latitude", 44.4361414)
                .containsEntry("longitude", 26.1027202)
                .containsEntry("formattedAddress", "Strada Lipscani 12, 030031 Bucharest, Romania")
                .containsEntry("capturedAt", null);
        assertThat(map(locationContent.get("freshness"))).containsEntry("stale", false);
        // structured results also come as JSON text for clients without structured-content support
        assertThat(text(location)).contains("\"state\":\"PARKED\"");

        CallToolResult apiKey = client.callTool(new CallToolRequest("get_api_key_status", Map.of()));
        assertThat(apiKey.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(structured(apiKey))
                .containsEntry("keyExpiresAt", "2026-12-31T00:00:00Z")
                .containsEntry("rateLimitLimit", 20)
                .containsEntry("rateLimitRemaining", 19);

        assertThat(upstreamRequests).singleElement().asString().startsWith("/api/v1/vehicles/" + VIN);
        assertThat(receivedApiKeys).containsExactly(API_KEY);
        assertThat(serverStderr.toString()).contains("Started SkodaApplication");
    }

    @Test
    void reportsUpstreamFailuresAsToolErrors() {
        stubUpstream(429, "application/problem+json", """
                {"type":"https://public.api.connect.skoda-auto.cz/problems/rate-limit-exceeded",
                 "title":"Too Many Requests","status":429,"detail":"Rate limit exceeded"}""",
                Map.of("Retry-After", "120"));
        client = startServer("--spring.profiles.active=mcp");
        client.initialize();

        CallToolResult result = client.callTool(new CallToolRequest("get_vehicle_status", Map.of()));

        assertThat(result.isError()).isTrue();
        assertThat(text(result))
                .startsWith("get_vehicle_status failed: MySkoda rate limit exceeded.")
                .contains("do not call the tool again for 120 seconds", "retryAfterSeconds: 120",
                        "upstreamStatus: 429")
                .doesNotContain(API_KEY);
    }

    /**
     * MCP clients stop a stdio server by closing its stdin (and only signal it if it does not exit). The
     * server must keep running while stdin is open, also after a tool call went upstream, and exit with
     * code 0 once stdin reaches end-of-input, writing nothing but JSON-RPC to stdout. Driven with a raw
     * process because the SDK client's {@code closeGracefully()} destroys the process instead.
     */
    @Test
    @Timeout(value = 90, threadMode = Timeout.ThreadMode.SEPARATE_THREAD) // blocking pipe reads
    void keepsRunningWhileStdinIsOpenAndExitsCleanlyWhenItIsClosed() throws Exception {
        stubUpstream(200, "application/json", fixture("vehicle-full.json"), Map.of());
        ProcessBuilder builder = new ProcessBuilder(serverCommand());
        builder.environment().putAll(serverEnvironment());
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process server = builder.start();
        try {
            OutputStream stdin = server.getOutputStream();
            BufferedReader stdout = new BufferedReader(
                    new InputStreamReader(server.getInputStream(), StandardCharsets.UTF_8));

            send(stdin, """
                    {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",\
                    "capabilities":{},"clientInfo":{"name":"it","version":"1"}}}""");
            assertThat(stdout.readLine()).startsWith("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":");
            send(stdin, """
                    {"jsonrpc":"2.0","method":"notifications/initialized"}""");
            send(stdin, """
                    {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_vehicle_range",\
                    "arguments":{}}}""");
            assertThat(stdout.readLine()).startsWith("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":");
            assertThat(upstreamRequests).hasSize(1);

            // idle with stdin open: must not exit on its own
            assertThat(server.waitFor(3, TimeUnit.SECONDS)).as("server exited while stdin was open").isFalse();

            stdin.close();
            assertThat(server.waitFor(15, TimeUnit.SECONDS)).as("server exited after stdin was closed").isTrue();
            assertThat(server.exitValue()).isZero();
            assertThat(stdout.readLine()).as("stdout after the last response").isNull();
        } finally {
            server.destroyForcibly();
        }
    }

    private static void send(OutputStream stdin, String message) throws IOException {
        stdin.write((message + "\n").getBytes(StandardCharsets.UTF_8));
        stdin.flush();
    }

    private McpSyncClient startServer(String... args) {
        List<String> command = serverCommand(args);
        ServerParameters parameters = ServerParameters.builder(command.getFirst())
                .args(command.subList(1, command.size()))
                .env(serverEnvironment())
                .build();
        StdioClientTransport transport = new StdioClientTransport(parameters, McpJsonDefaults.getMapper());
        transport.setStdErrorHandler(line -> serverStderr.append(line).append('\n'));
        return McpClient.sync(transport)
                .initializationTimeout(TIMEOUT)
                .requestTimeout(TIMEOUT)
                .build();
    }

    private static List<String> serverCommand(String... args) {
        String jar = System.getProperty("skoda.it.jar");
        assertThat(jar).as("system property skoda.it.jar (set by failsafe)").isNotBlank();
        assertThat(Path.of(jar)).exists();

        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar));
        command.addAll(List.of(args));
        return command;
    }

    private Map<String, String> serverEnvironment() {
        return Map.of(
                "SKODA_VIN", VIN,
                "SKODA_API_KEY", API_KEY,
                "SKODA_PUBLIC_API_URL", "http://127.0.0.1:" + upstream.getAddress().getPort(),
                "SKODA_CACHE_TTL", "5m");
    }

    private void stubUpstream(int status, String contentType, String body, Map<String, String> headers) {
        upstream.createContext("/", exchange -> {
            upstreamRequests.add(exchange.getRequestURI().toString());
            receivedApiKeys.add(exchange.getRequestHeaders().getFirst("X-API-Key"));
            respond(exchange, status, contentType, body, headers);
        });
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body,
            Map<String, String> headers) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        headers.forEach(exchange.getResponseHeaders()::set);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String fixture(String name) {
        try (InputStream in = McpStdioServerIT.class.getResourceAsStream("/upstream/" + name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Map<String, Object> structured(CallToolResult result) {
        return map(result.structuredContent());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private static String text(CallToolResult result) {
        assertThat(result.content()).isNotEmpty().first().isInstanceOf(TextContent.class);
        return ((TextContent) result.content().getFirst()).text();
    }
}
