package com.gd.skoda.mcp;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;

/**
 * Matches when the MCP server runs, i.e. under the same property as Spring AI's
 * {@code McpServerAutoConfiguration} ({@code spring.ai.mcp.server.enabled}, default {@code true}).
 * The {@code mcp} profile sets it to {@code true}, the {@code api} profile to {@code false}.
 *
 * <p>Used instead of {@code @Profile("mcp")}: without an explicit profile Boot ends up with the
 * {@code default} profile (see {@code McpProfileApplicationTest}), so {@code @Profile("mcp")} beans
 * would be missing in the default run mode.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnBooleanProperty(name = "spring.ai.mcp.server.enabled", matchIfMissing = true)
public @interface ConditionalOnMcpServer {
}
