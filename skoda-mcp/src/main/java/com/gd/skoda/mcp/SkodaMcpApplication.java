package com.gd.skoda.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(SkodaRuntimeHints.class)
public class SkodaMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkodaMcpApplication.class, args);
    }
}
