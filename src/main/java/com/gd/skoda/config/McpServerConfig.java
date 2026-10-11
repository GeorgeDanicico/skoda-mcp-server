package com.gd.skoda.config;

import com.gd.skoda.error.SkodaApiProblems;
import com.gd.skoda.mcp.ConditionalOnMcpServer;
import com.gd.skoda.mcp.McpToolErrors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Support beans for the generated MCP tools ({@code com.gd.skoda.mcp.generated}). Active whenever the
 * MCP server is, i.e. in the {@code mcp} run mode, not in {@code api}.
 *
 * <p>The tools themselves are generated {@code @Component}s picked up by component scanning and found
 * by Spring AI's {@code @McpTool} annotation scanner; their transport (stdio) and server settings are
 * in {@code application-mcp.yaml}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnMcpServer
public class McpServerConfig {

    @Bean
    McpToolErrors mcpToolErrors(SkodaApiProblems skodaApiProblems) {
        return new McpToolErrors(skodaApiProblems);
    }
}
