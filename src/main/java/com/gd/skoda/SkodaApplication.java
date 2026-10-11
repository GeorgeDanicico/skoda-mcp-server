package com.gd.skoda;

import com.gd.skoda.config.NativeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Read-only bridge to the MyŠkoda Public API.
 *
 * <p>Runs as a stdio MCP server ({@code mcp} profile, the default) or as a REST API
 * ({@code api} profile).
 */
@SpringBootApplication
@ImportRuntimeHints(NativeHints.class)
public class SkodaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkodaApplication.class, args);
    }
}
