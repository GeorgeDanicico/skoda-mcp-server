package com.gd.skoda.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.util.ClassUtils;
import org.yaml.snakeyaml.Yaml;

/**
 * Guard against drift between the contract and the MCP tools: every operation of
 * {@code skoda-service-api.yaml} that is not {@code x-mcp-exclude: true} has exactly one {@code @McpTool}
 * with the operation's {@code x-mcp-name}, its summary and description, and matching hints - and there
 * are no other tools. Independent of how the tools are produced (template today, hand-written as fallback).
 *
 * <p>JVM only: reads the spec from the source tree and scans the classpath for class files, neither of
 * which exists in a native image (the build is checked on the JVM anyway).
 */
@DisabledInNativeImage
class McpToolsContractTest {

    private static final Path SPEC = Path.of("src/main/openapi/skoda-service-api.yaml");
    private static final String TOOLS_PACKAGE = "com.gd.skoda.mcp";
    private static final Set<String> HTTP_METHODS = Set.of("get", "put", "post", "delete", "options", "head",
            "patch", "trace");

    private static List<Operation> operations;
    private static Map<String, List<Method>> toolsByName;

    @BeforeAll
    static void load() throws IOException {
        operations = readOperations();
        toolsByName = findTools();
    }

    @Test
    void specDeclaresOperationsWithToolNames() {
        assertThat(operations).isNotEmpty();
        assertThat(operations).filteredOn(op -> !op.excluded())
                .allSatisfy(op -> assertThat(op.toolName()).as(op.operationId()).matches("[a-z][a-z0-9_]*"));
    }

    @Test
    void everyIncludedOperationHasExactlyOneTool() {
        for (Operation op : operations) {
            List<Method> tools = toolsByName.getOrDefault(op.toolName(), List.of());
            if (op.excluded()) {
                assertThat(tools).as("excluded operation %s must not be a tool", op.operationId()).isEmpty();
            } else {
                assertThat(tools).as("tools named %s", op.toolName()).hasSize(1);
                assertThat(tools.getFirst().getName()).as(op.toolName()).isEqualTo(op.operationId());
            }
        }
    }

    @Test
    void thereAreNoToolsWithoutOperation() {
        Set<String> expected = operations.stream()
                .filter(op -> !op.excluded())
                .map(Operation::toolName)
                .collect(Collectors.toSet());
        assertThat(toolsByName.keySet()).isEqualTo(expected);
    }

    @Test
    void toolDescriptionsAreSummaryPlusDescription() {
        for (Operation op : included()) {
            String description = tool(op).description();
            assertThat(description).as(op.toolName()).contains(op.summary());
            assertThat(normalize(description)).as(op.toolName())
                    .isEqualTo(normalize(op.summary() + " " + op.description()));
        }
    }

    @Test
    void hintsMatchTheContract() {
        for (Operation op : included()) {
            McpTool tool = tool(op);
            assertThat(tool.annotations().readOnlyHint()).as(op.toolName()).isEqualTo(op.readOnly());
            if (op.readOnly()) {
                assertThat(tool.annotations().destructiveHint()).as(op.toolName()).isFalse();
                assertThat(tool.annotations().idempotentHint()).as(op.toolName()).isTrue();
            }
            assertThat(tool.generateOutputSchema()).as(op.toolName()).isTrue();
        }
    }

    private static List<Operation> included() {
        return operations.stream().filter(op -> !op.excluded()).toList();
    }

    private static McpTool tool(Operation op) {
        return toolsByName.get(op.toolName()).getFirst().getAnnotation(McpTool.class);
    }

    private static String normalize(String text) {
        return text.strip().replaceAll("\\s+", " ");
    }

    @SuppressWarnings("unchecked")
    private static List<Operation> readOperations() throws IOException {
        Map<String, Object> spec;
        try (Reader reader = Files.newBufferedReader(SPEC)) {
            spec = new Yaml().load(reader);
        }
        List<Operation> result = new ArrayList<>();
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) spec.get("paths");
        paths.forEach((path, pathItem) -> pathItem.forEach((method, value) -> {
            if (HTTP_METHODS.contains(method)) {
                Map<String, Object> op = (Map<String, Object>) value;
                result.add(new Operation((String) op.get("operationId"), (String) op.get("x-mcp-name"),
                        (String) op.get("summary"), (String) op.getOrDefault("description", ""),
                        Boolean.TRUE.equals(op.get("x-mcp-read-only")), Boolean.TRUE.equals(op.get("x-mcp-exclude"))));
            }
        }));
        return result;
    }

    private static Map<String, List<Method>> findTools() {
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        Map<String, List<Method>> tools = new LinkedHashMap<>();
        scanner.findCandidateComponents(TOOLS_PACKAGE).forEach(candidate -> {
            Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), null);
            Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(McpTool.class))
                    .forEach(method -> tools.computeIfAbsent(method.getAnnotation(McpTool.class).name(),
                            name -> new ArrayList<>()).add(method));
        });
        return tools;
    }

    private record Operation(String operationId, String toolName, String summary, String description,
            boolean readOnly, boolean excluded) {
    }
}
