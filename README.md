# skoda-mcp-server

An unofficial, **read-only** bridge to the official
[MyŠkoda Public API](https://public.api.connect.skoda-auto.cz/docs) for one Škoda vehicle.
The same application runs in one of two modes:

- **`mcp`** (default): a [Model Context Protocol](https://modelcontextprotocol.io) server over
  **stdio**, so an AI assistant (Claude Desktop, Claude Code, MCP Inspector and others) can ask
  where the car is parked, how much range is left, or whether it is locked.
- **`api`**: a small **REST API** with Swagger UI. It has no authentication of its own, so keep it on
  localhost or a trusted network.

It authenticates with a MyŠkoda **API key** only. It never sees your MyŠkoda password and cannot
send commands to the car.

Built with Java 25, Spring Boot 4.1, Spring AI 2.0 (MCP Java SDK) and openapi-generator. The
application also builds as GraalVM native executables, packaged in small Docker images.

## Contents

- [Features](#features)
- [Limitations](#limitations)
- [Getting a MyŠkoda API key](#getting-a-myškoda-api-key)
- [Configuration](#configuration)
- [Running](#running)
- [MCP client configuration](#mcp-client-configuration)
- [REST API](#rest-api)
- [Building and testing](#building-and-testing)
- [Design: one OpenAPI contract for REST and MCP](#design-one-openapi-contract-for-rest-and-mcp)
- [Project layout](#project-layout)
- [Attribution](#attribution)

## Features

| MCP tool | REST endpoint | What it returns |
|---|---|---|
| `get_vehicle_overview` | `GET /api/v1/vehicle` | Everything at once: name, licence plate, location, range, lock/door/window/light state, odometer |
| `get_vehicle_location` | `GET /api/v1/vehicle/location` | `PARKED` (with coordinates and address) or `IN_MOTION` (no position while driving) |
| `get_vehicle_range` | `GET /api/v1/vehicle/range` | Total range in km, fuel level / battery state of charge and range per engine, AdBlue range, car type |
| `get_vehicle_status` | `GET /api/v1/vehicle/status` | Locked, doors, windows, trunk, bonnet, sunroof, lights, odometer in km |
| `get_api_key_status` | `GET /api/v1/system/api-key` | API key expiry (and days left), remaining request quota, time of the last MyŠkoda call |

- All tools are marked read-only (`readOnlyHint`) and return **structured content** with an output
  schema. The schema includes the contract's field descriptions, so the model knows the units and
  that `null` means "unknown".
- Every vehicle response has a `freshness` object: `fetchedAt` (when the data came from MyŠkoda),
  `stale` (older data served because a refresh was not possible) and `unavailable` (parts MyŠkoda
  did not deliver, for example when location sharing is disabled in the car). The car's own
  measurement time is the `capturedAt` field inside the data.
- Failures are reported in a way a model can act on. An MCP tool error says whether retrying
  helps, and for how long to wait. A REST error is an RFC 9457 `application/problem+json`
  response with `retryAfterSeconds` and `upstreamStatus` where known. The API key is never
  included.
- If the key expires in less than 7 days, the server logs a warning (at most once a day).

## Limitations

- **Read-only, and limited to what the public API offers.** The MyŠkoda Public API (v1.1.0) has
  no lock/unlock, honk/flash, trips or fuel consumption, so none of these are available here.
  Charging and climate commands are not implemented either.
- **One vehicle**: the VIN is configured on the server. The public API has no vehicle list, and the
  key is bound to the vehicles you selected when you created it.
- **20 requests per hour per vehicle.** This is MyŠkoda's limit, and every request counts, even
  failed ones. The server is built around it:
  - All tools and endpoints share **one cached snapshot**. One upstream call fetches every part,
    and the snapshot is reused for `SKODA_CACHE_TTL` (default 5 minutes). Concurrent requests
    share a single fetch.
  - When fewer than 3 requests are left in the current window (`RateLimit-Remaining` ≤ 2), or
    MyŠkoda asked to wait (`Retry-After`), the server does not call MyŠkoda. It serves the last
    snapshot with `stale: true`.
  - On HTTP 429, 5xx or a timeout, it serves the last snapshot as `stale: true`. Without a
    snapshot it returns an error that includes `retryAfterSeconds` when known.
  - `get_api_key_status` never calls MyŠkoda. Before the first vehicle request all its fields are
    `null`.
- The parking position has no timestamp in the public API (`location.capturedAt` is always `null`).
- An MCP request that is still running when the client closes stdin gets no response. The MCP
  SDK's stdio transport stops writing once stdin is closed. Clients normally wait for their
  responses before closing, so this rarely matters.

## Getting a MyŠkoda API key

1. In the **MyŠkoda app**, open the **API keys** section. The link <https://go.skoda.eu/api-keys>
   leads there (open it on the phone that has the app installed).
2. Create a key and select the vehicle (VIN) that the server should read.
3. Set the key as `SKODA_API_KEY` and the vehicle's VIN as `SKODA_VIN`.

Keys **expire**. Check `get_api_key_status` (or `GET /api/v1/system/api-key`) for `keyExpiresAt`
and `daysUntilExpiry`, and look for the warning in the log. When a key expires or is revoked,
every vehicle call fails with a message telling you to create a new key in the app. On the REST
API this is a `503`, because the server, not the caller, needs fixing.

## Configuration

Settings come from environment variables. In addition, an optional `.env` file in the **working
directory** is read (`KEY=value` lines, same names). Copy [`.env.example`](.env.example) to `.env`.
The file is ignored by Git and excluded from Docker builds. Settings are validated at startup:
an invalid or missing value stops the server with a readable message on stderr.

| Environment variable | Spring property | Default | Notes |
|---|---|---|---|
| `SKODA_VIN` | `skoda.vin` | – (required) | 17 characters, upper case |
| `SKODA_API_KEY` | `skoda.public-api.key` | – (required) | MyŠkoda Public API key, sent as `X-API-Key` |
| `SKODA_PUBLIC_API_URL` | `skoda.public-api.base-url` | `https://public.api.connect.skoda-auto.cz` | change only for testing against a stub |
| `SKODA_PUBLICAPI_TIMEOUT` | `skoda.public-api.timeout` | `15s` | connect/read timeout for MyŠkoda calls |
| `SKODA_CACHE_TTL` | `skoda.cache.ttl` | `5m` | how long a vehicle snapshot is reused (`30s`, `5m`, `1h`, …) |

The run mode is the Spring profile: `mcp` is the default, and `--spring.profiles.active=api` (or
`SPRING_PROFILES_ACTIVE=api`) selects the REST API. The native executables are fixed to one mode
at build time (see below).

| | `mcp` | `api` |
|---|---|---|
| Transport | MCP over stdin/stdout | HTTP on port `8080` |
| Logging | stderr only (stdout carries the protocol) | console |
| Auth | none (local process) | none: anyone who can reach port `8080` can read the vehicle data |
| Health | – | `GET /actuator/health` on the management port `8081` |

## Running

### JVM (jar)

Requires JDK 25.

```sh
./mvnw -DskipTests package            # Windows: .\mvnw.cmd -DskipTests package
export SKODA_VIN=TMB... SKODA_API_KEY=...

# stdio MCP server (the default mode). Stdout is reserved for MCP messages.
java -jar target/skoda-mcp-server-1.0.0-SNAPSHOT.jar

# REST API
java -jar target/skoda-mcp-server-1.0.0-SNAPSHOT.jar --spring.profiles.active=api
```

To try the MCP server interactively, run
`npx @modelcontextprotocol/inspector java -jar target/skoda-mcp-server-1.0.0-SNAPSHOT.jar`.

The stdio server runs until its client closes stdin, which is how MCP clients stop a stdio
server. It then exits with code 0.

### Native executables in Docker

The [`Dockerfile`](Dockerfile) builds a GraalVM 25 native executable and puts it in a distroless
image that runs as a non-root user. Spring AOT fixes the run mode at build time, so each mode is
its own image. The executables activate their mode themselves, so don't set a profile at run
time. No local GraalVM is needed. A build takes about 3 minutes.

```sh
docker build -t skoda-mcp .                                  # BUILD_PROFILE=mcp is the default
docker build -t skoda-api --build-arg BUILD_PROFILE=api .
```

Run the MCP image with `-i` (stdin open). `-e NAME` without a value passes the variable through
from your environment, so secrets don't appear on the command line:

```sh
docker run -i --rm -e SKODA_VIN -e SKODA_API_KEY skoda-mcp
```

Closing stdin stops the server, and `--rm` removes the container.

Run the REST API image:

```sh
docker run -d --name skoda-api -p 127.0.0.1:8080:8080 -e SKODA_VIN -e SKODA_API_KEY skoda-api
```

The health endpoint is on port `8081` inside the container. It is not exposed. Use it for
orchestrator health checks (`http://<container>:8081/actuator/health`) and don't publish it. The
images contain no shell or curl, so they have no Docker `HEALTHCHECK`.

The images are about 185 MB (mcp) and 212 MB (api). They start in about 0.2–0.3 s and use about
70 MiB of memory.

### Local native build

Requires a GraalVM 25 JDK as `JAVA_HOME`. On Windows you also need the MSVC build tools. If you
don't have these, use Docker.

```sh
./mvnw -Pnative,mcp -DskipTests package   # -> target/skoda-mcp
./mvnw -Pnative,api -DskipTests package   # -> target/skoda-api
```

In PowerShell, quote the profile list: `'-Pnative,mcp'`. The executable built in Docker is a Linux
x86-64 binary that needs glibc 2.34 or later. To copy it out of the image:
`docker create --name tmp skoda-mcp && docker cp tmp:/app/. ./skoda-native && docker rm tmp`.

## MCP client configuration

All examples use the `mcpServers` format of Claude Desktop (`claude_desktop_config.json`), Claude
Code (`.mcp.json`) and many other clients. Pass the settings in `env`. A `.env` file is resolved
against the client's working directory, which you usually don't control.

**JVM jar**

```json
{
  "mcpServers": {
    "skoda": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/skoda-mcp-server-1.0.0-SNAPSHOT.jar"],
      "env": {
        "SKODA_VIN": "TMBJJ7NE8L0123456",
        "SKODA_API_KEY": "<MyŠkoda API key>",
        "SKODA_CACHE_TTL": "5m"
      }
    }
  }
}
```

**Docker** (native image). The `-e NAME` flags without values take the values from `env`:

```json
{
  "mcpServers": {
    "skoda": {
      "command": "docker",
      "args": ["run", "-i", "--rm", "-e", "SKODA_VIN", "-e", "SKODA_API_KEY", "-e", "SKODA_CACHE_TTL", "skoda-mcp"],
      "env": {
        "SKODA_VIN": "TMBJJ7NE8L0123456",
        "SKODA_API_KEY": "<MyŠkoda API key>",
        "SKODA_CACHE_TTL": "5m"
      }
    }
  }
}
```

**Native executable**

```json
{
  "mcpServers": {
    "skoda": {
      "command": "/opt/skoda-mcp-server/skoda-mcp",
      "env": {
        "SKODA_VIN": "TMBJJ7NE8L0123456",
        "SKODA_API_KEY": "<MyŠkoda API key>"
      }
    }
  }
}
```

With Claude Code you can also register the server from the command line:
`claude mcp add skoda -e SKODA_VIN=TMB... -e SKODA_API_KEY=... -- java -jar /absolute/path/to/skoda-mcp-server-1.0.0-SNAPSHOT.jar`.

## REST API

Start the server in `api` mode (see [Running](#running)), then:

```sh
curl -s http://localhost:8080/api/v1/vehicle
curl -s http://localhost:8080/api/v1/vehicle/location
curl -s http://localhost:8080/api/v1/vehicle/range
curl -s http://localhost:8080/api/v1/vehicle/status
curl -s http://localhost:8080/api/v1/system/api-key

curl -s http://localhost:8081/actuator/health             # {"status":"UP"} (management port)
```

Example (`/api/v1/vehicle/location`, abridged):

```json
{
  "data": { "state": "PARKED", "latitude": 44.4361414, "longitude": 26.1027202,
            "formattedAddress": "Strada Lipscani 12, 030031 Bucharest, Romania", "capturedAt": null },
  "freshness": { "fetchedAt": "2026-10-11T09:12:03Z", "stale": false, "unavailable": [] }
}
```

- **Swagger UI**: <http://localhost:8080/swagger-ui.html>. It shows the actual contract, which is
  served as <http://localhost:8080/skoda-service-api.yaml>.
- Responses carry `Cache-Control: no-store`. The server caches MyŠkoda data itself.
- The API has no authentication. It exposes your car's location, so publish port `8080` only
  on localhost (as above) or put it behind a reverse proxy that adds authentication.
- Error status codes: `429` rate limit with `Retry-After`; `503`
  MyŠkoda unavailable, **or** the API key expired, is invalid or does not cover the vehicle (the
  operator has to fix this, so it isn't `401`/`403`); `502` MyŠkoda rejected the VIN or sent an
  invalid response; `504` MyŠkoda timeout.

## Building and testing

```sh
./mvnw clean verify
```

This generates code from the OpenAPI contracts, compiles, runs the unit tests (Surefire), packages
the jar and runs the integration test `McpStdioServerIT` (Failsafe). The integration test starts
the jar as a child process against a local stub of the MyŠkoda API. It talks MCP over stdio
(initialize, tools/list, tool calls, upstream errors) and checks that the process keeps running
while stdin is open and exits with code 0 after stdin is closed. Tests make no network calls.

| What | Command |
|---|---|
| Unit tests only | `./mvnw test` |
| Unit and integration tests | `./mvnw verify` |
| Tests as a native executable (needs GraalVM 25) | `./mvnw -PnativeTest test` |
| The same in Docker | `docker build --target native-test .` |
| Smoke test of both native images (needs Docker and PowerShell 5.1 or 7) | `./scripts/smoke-test-native.ps1 -Build` |

The smoke test ([`scripts/smoke-test-native.ps1`](scripts/smoke-test-native.ps1)) starts a stub
MyŠkoda API ([`scripts/smoke/upstream_stub.py`](scripts/smoke/upstream_stub.py) in
`python:3.13-alpine`) on a private Docker network. It checks the following:

- **MCP image**: the protocol over stdio, that every stdout line is JSON-RPC, output schemas,
  tool results, exit code 0 when stdin closes, and fail-fast on invalid configuration.
- **API image**: health, authentication, the endpoints, Swagger UI, and that the cache makes
  exactly one upstream call.

Without `-Build` it tests the existing `skoda-mcp:native` / `skoda-api:native` images. Use
`-McpImage`, `-ApiImage`, `-SkipMcp` and `-SkipApi` to choose what to test.

CI ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) runs `./mvnw clean verify` on JDK 25.
It then builds both native images, runs the smoke test against each, and runs the native tests.
Nothing is pushed to a registry.

## Design: one OpenAPI contract for REST and MCP

```
MCP client ──stdio──► generated @McpTool methods ─┐
                                                  ├─► VehicleApiFacade ─► VehicleService ─► PublicApiClient ─► MyŠkoda Public API
HTTP client ─────────► REST controllers ──────────┘     (domain → DTO)    (snapshot cache,    (generated
                       (implement generated                               rate-limit guard)   @HttpExchange client)
                        interfaces)
```

Two OpenAPI files in [`src/main/openapi/`](src/main/openapi) drive the code generation:

1. **`myskoda-public-api.yaml`**: Škoda's contract, vendored unchanged. openapi-generator
   (`spring-http-interface`) generates the `@HttpExchange` client and models for the upstream
   API.
2. **`skoda-service-api.yaml`**: this application's contract, the single source for both
   interfaces. One generator run produces:
   - the Spring MVC interfaces and DTOs (`com.gd.skoda.api.generated`). The thin `@Profile("api")`
     controllers in `com.gd.skoda.api` implement them.
   - the **MCP tools** (`com.gd.skoda.mcp.generated.<Tag>ApiMcpTools`), rendered by the custom
     template [`templates/mcp/mcpTools.mustache`](src/main/openapi/templates/mcp/mcpTools.mustache)
     (registered in `skoda-service-api-generator.yaml`). Each operation becomes an `@McpTool`
     method:
     - The tool description is the operation's `summary` plus its `description`.
     - The output schema comes from the 200 response DTO, including the `@Schema` field
       descriptions.
     - The method calls the facade method with the same name as the `operationId`.

The REST endpoints and the MCP tools therefore share their logic, descriptions and schemas. All
of it is plain annotated Java at compile time, with no spec parsing at run time, which keeps it
friendly to AOT and native images. `McpToolsContractTest` checks that the generated tools match
the spec.

Vendor extensions on operations:

| Extension | Effect |
|---|---|
| `x-mcp-name` | MCP tool name (snake_case); required for every tool |
| `x-mcp-read-only: true` | `readOnlyHint=true, destructiveHint=false, idempotentHint=true`; otherwise the conservative MCP defaults |
| `x-mcp-exclude: true` | REST only, no MCP tool |

### Adding an operation

1. Add the operation to `skoda-service-api.yaml` with:
   - an `operationId`, a tag, a `summary` and a `description` written for an LLM reader (units,
     what `null` means);
   - a 200 response schema and the shared error responses;
   - `x-mcp-name` and `x-mcp-read-only` (or `x-mcp-exclude: true`).
2. Run `./mvnw generate-sources`. This produces the interface method in
   `com.gd.skoda.api.generated.<Tag>Api` and the tool method in `<Tag>ApiMcpTools`, which calls
   `VehicleApiFacade.<operationId>(…)`.
3. Implement `<operationId>` in `VehicleApiFacade`. Use the same parameters, and return the
   generated DTO; map from the domain in `ServiceApiMapper`. Then implement the interface method
   in the controller: one line, `ApiResponses.ok(facade.<operationId>())`. A new tag needs a new
   `@RestController @Profile("api")` class that implements `<Tag>Api`.
4. Tests: `McpToolsContractTest` picks up the new tool automatically. Add facade, controller
   (`@WebMvcTest`) and mapping tests. Parameters become `@McpToolParam` arguments, with their
   descriptions taken from the spec.

## Project layout

```
.
├── pom.xml, mvnw, mvnw.cmd, .mvn/      Maven build (single module, Maven wrapper)
├── Dockerfile, .dockerignore           native images (BUILD_PROFILE=mcp|api) and native-test target
├── .env.example                        configuration template (copy to .env)
├── docs/REWRITE_PLAN.md                design decisions and implementation notes
├── scripts/                            native smoke test + MyŠkoda stub
├── .github/workflows/                  ci.yml (build, test, native), notify-vps.yml
└── src/
    ├── main/openapi/                   myskoda-public-api.yaml, skoda-service-api.yaml,
    │                                   generator config, templates/mcp/mcpTools.mustache
    ├── main/java/com/gd/skoda/
    │   ├── SkodaApplication.java
    │   ├── config/     properties (validated records), bean wiring, native hints
    │   ├── upstream/   MyŠkoda client facade, API key interceptor/status, error mapping
    │   ├── vehicle/    VehicleService, SnapshotCache, domain records
    │   ├── api/        VehicleApiFacade, DTO mapper, REST controllers
    │   ├── mcp/        tool error translation, @ConditionalOnMcpServer
    │   └── error/      problem+json mapping (REST advice, problem types)
    ├── main/resources/ application.yaml, application-mcp.yaml, application-api.yaml, logback-spring.xml
    └── test/           unit tests, McpStdioServerIT, fixtures (test/resources/upstream)
```

Generated code is written to `target/generated-sources/openapi` and is not committed.

## Attribution

This project was inspired by the work of Nicholas Meyers, particularly the original
[api-skoda](https://github.com/nicholasM95/api-skoda) and
[skoda-api-client](https://github.com/nicholasM95/skoda-api-client) projects.

This project is unofficial and is not affiliated with or endorsed by Škoda Auto or the
Volkswagen Group. "Škoda" and "MyŠkoda" are trademarks of their respective owners.
