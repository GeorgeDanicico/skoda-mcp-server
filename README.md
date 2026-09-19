# Skoda Java API workspace

This workspace contains the Java projects used to access Skoda vehicle data and
remote vehicle functions:

- `api-skoda` provides the HTTP API server.
- `skoda-api-client` provides the Java client library.
- `skoda-api-client-tester` provides a small command-line test application.
- `skoda-mcp` provides a minimal Spring AI MCP server over stdio.

The owned Java packages use the `com.gd` base namespace.

## Skoda MCP server

The MCP server uses Java 21, Spring Boot 4.1, and Spring AI 2.0. It deliberately
includes only the stdio MCP starter; it does not expose an HTTP, SSE, or
Streamable HTTP endpoint.

Install the local Java API client and build the MCP server:

```bash
mvn -f skoda-api-client/pom.xml clean install -DskipTests -Dgpg.skip=true
mvn -f skoda-mcp/pom.xml clean package
```

Configure the MCP server through environment variables (the corresponding Spring
property names are shown below):

- `skoda.email` — MySkoda account email (required when a tool is called)
- `skoda.password` — MySkoda account password (required when a tool is called)
- `skoda.vin` — default vehicle VIN (required by vehicle-specific tools unless
  the tool call supplies a VIN override)
- `skoda.spin` — four-digit MySkoda S-PIN (required only for lock/unlock)
- `skoda.api-base-url` — optional API server URL, for example
  `http://localhost:8080`; when omitted, the Java client's default is used

The properties read `SKODA_EMAIL`, `SKODA_PASSWORD`, `SKODA_VIN`, and
`SKODA_SPIN`. The optional server URL reads `SKODA_SERVER`, matching the tester,
and also accepts the legacy `SKODA_API_BASE_URL` variable.

Run it over stdio with:

```bash
java -jar skoda-mcp/target/skoda-mcp.jar
```

Example MCP client configuration:

```json
{
  "mcpServers": {
    "skoda": {
      "command": "/path/to/java-21/bin/java",
      "args": ["-jar", "/absolute/path/to/skoda-mcp/target/skoda-mcp.jar"],
      "env": {
        "SKODA_EMAIL": "you@example.com",
        "SKODA_PASSWORD": "your-password",
        "SKODA_VIN": "your-vehicle-vin",
        "SKODA_SPIN": "1234"
      }
    }
  }
}
```

Add `"SKODA_SERVER": "http://localhost:8080"` only when intentionally using a
custom or local API server. With no server override, the client uses the same
default remote API as `skoda-api-client-tester`.

Available tools are `list_vehicles`, `get_vehicle_location`,
`get_vehicle_status`, `get_vehicle_range`, `flash_vehicle_lights`,
`honk_and_flash_vehicle`, `lock_vehicle`, and `unlock_vehicle`.

Vehicle-specific tools can be called with an empty argument object to use
`SKODA_VIN`. Pass `{ "vin": "another-vin" }` only when overriding the configured
vehicle for that call.

## Attribution

This Skoda Java API was inspired by the work of Nicholas Meyers, particularly the
original [api-skoda](https://github.com/nicholasM95/api-skoda) and
[skoda-api-client](https://github.com/nicholasM95/skoda-api-client) projects.

This project is unofficial and is not affiliated with or endorsed by Skoda Auto or
the Volkswagen Group.
