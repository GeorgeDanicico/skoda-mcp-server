# Skoda MCP server – consolidation and rewrite plan

Status: **implemented** (steps 1–7) · 2026-10-11. Where the implementation differs from
this plan, see [6. Implementation notes](#6-implementation-notes). The [README](../README.md)
describes how to use the result.

## Decisions

| # | Decision |
|---|---|
| D1 | **Public API only.** Auth is the MyŠkoda API key. No private mobile API, no email/password, no access/refresh token, no S-PIN. |
| D2 | No Telegram bridge – the old `gateway` profile and `/api/v1/car` endpoints are dropped without replacement. |
| D3 | No MCP over HTTP. MCP is stdio only. |
| D4 | No charging or climate commands (combustion car). The app is **read-only**. |
| D5 | **Java 25**, GraalVM 25. |
| D6 | The REST API has **no inbound authentication** (the bearer token was removed after implementation). The MyŠkoda API key is the only credential; expose port 8080 only on localhost or a trusted network. |

Consequence: lock/unlock, honk/flash, trips and fuel consumed are **out of scope**
because the public API doesn't provide them. Lock *state*, fuel level and
odometer are available and included.

## 1. Where we are today

Three Maven projects that only work together:

| Folder | What it is |
|---|---|
| `api-skoda` | Spring Boot 3.5 REST server with 7 Feign clients forwarding a Bearer token to the private MySkoda mobile backend. |
| `skoda-api-client` | Java library (OkHttp + Gson) calling `api-skoda`; logs in with email + password via `be.nicholasmeyers:skoda-connector`. |
| `skoda-mcp` | Spring Boot 4.1 + Spring AI 2.0 stdio MCP server plus a `gateway` profile; needs `api-skoda` on `127.0.0.1:8080`. |

Call chain today: MCP client → skoda-mcp → skoda-api-client → api-skoda → MySkoda private API.
All three are replaced by one app that calls the public API directly.

## 2. MyŠkoda Public API (what we use)

Source: <https://public.api.connect.skoda-auto.cz/docs>, contract
`/docs/myskoda-public-api.yaml` (OpenAPI 3.0.3, v1.1.0).

- **Auth**: `X-API-Key` header. Key created in the MyŠkoda app, bound to selected
  vehicles, expires. Every 2xx returns `X-API-Key-Expires-At`.
- **Only endpoint we need**: `GET /api/v1/vehicles/{vin}?include=…`, parts used:
  `info, status, fuelStatus, odometer, parkingPosition` (`charging` is requested
  too so range also works if the car is ever a PHEV/EV – cheap, same call).
- **Rate limit: 20 requests/hour per VIN**, every request counts (incl. 5xx),
  429 + `Retry-After` when exceeded; `RateLimit-Limit/Remaining/Reset` headers.
- **Partial data**: omitted parts are listed in `errors[]`; omitted fields mean "unknown".
- **Errors**: RFC 9457 problem+json (`api-key-expired` 401, `api-key-not-authorized`
  403, `rate-limit-exceeded` 429, …).
- No vehicle-list endpoint → VIN is configured.

## 3. Target architecture

One Spring Boot 4 application, one Maven module at the repository root.

```
              ┌──────────── skoda-mcp-server (single app) ─────────────┐
MCP client ─► │ stdio MCP tools ──┐                                    │
(stdio)       │                   ├─► VehicleService ─► PublicApiClient ┼─► public.api.connect.skoda-auto.cz
HTTP client ─►│ REST controllers ─┘   (snapshot cache,  (generated      │   (X-API-Key)
(api profile) │ (generated from        rate-limit       @HttpExchange) │
              │  our OpenAPI)          guard)                          │
              └────────────────────────────────────────────────────────┘
```

### 3.1 Tech stack

| Concern | Choice |
|---|---|
| Build | Maven, single module, `spring-boot-starter-parent` 4.1.x |
| Java | 25, native build with GraalVM 25 |
| MCP | Spring AI 2.0 `spring-ai-starter-mcp-server` (stdio, SYNC), `@McpTool` |
| REST | `spring-boot-starter-webmvc`, virtual threads |
| HTTP out | Spring Framework 7 HTTP interface clients (`@HttpExchange` + `RestClient`). No Feign/OkHttp/Gson. |
| JSON | Jackson 3 (Boot 4 default) |
| Contracts | `openapi-generator-maven-plugin` 7.20+ (upstream client + our API + MCP tools) |
| Docs UI | springdoc-openapi serving our spec (`api` profile) |
| Native | Boot `native` profile + `native-maven-plugin`; `nativeTest` |
| Tests | JUnit, `MockRestServiceServer`, `@WebMvcTest`, MCP stdio smoke test |

### 3.2 Package layout (`com.gd.skoda`)

```
com.gd.skoda
├── SkodaApplication
├── config/        SkodaProperties (validated record), PublicApiClientConfig, NativeHints
├── upstream/      GENERATED client + models from myskoda-public-api.yaml,
│                  ApiKeyInterceptor, ApiKeyStatus, problem+json → exceptions
├── vehicle/       VehicleService, SnapshotCache, domain records
│                  (VehicleOverview, Location, Range, Status, ApiKeyInfo)
├── api/           delegate implementations of GENERATED REST interfaces + auth filter
├── mcp/           GENERATED @McpTool class (custom template) – see 3.4
└── error/         exception types, @RestControllerAdvice → ProblemDetail
```

### 3.3 Contracts (OpenAPI first)

In `src/main/openapi/`:

1. **`myskoda-public-api.yaml`** – Skoda's contract, vendored verbatim. Generator
   `spring`, `library=spring-http-interface`, output under `target/generated-sources`.
2. **`skoda-service-api.yaml`** – our contract, single source for REST and MCP:

| operationId | REST | MCP tool | Data |
|---|---|---|---|
| `getVehicleOverview` | `GET /api/v1/vehicle` | `get_vehicle_overview` | name, plate, location, range, lock/door/window/light state, odometer, captured timestamp, upstream `errors[]` |
| `getVehicleLocation` | `GET /api/v1/vehicle/location` | `get_vehicle_location` | `PARKED`/`IN_MOTION`, lat/lon, address |
| `getVehicleRange` | `GET /api/v1/vehicle/range` | `get_vehicle_range` | total range km, fuel %, per-engine range, AdBlue range, SoC if present |
| `getVehicleStatus` | `GET /api/v1/vehicle/status` | `get_vehicle_status` | locked, doors, windows, lights, trunk, bonnet, sunroof, odometer km |
| `getApiKeyStatus` | `GET /api/v1/system/api-key` | `get_api_key_status` | key expiry, rate-limit remaining/reset, last upstream call time |

   All operations are read-only (`x-mcp-read-only: true`). Every vehicle response
   carries `capturedAt` (car timestamp), `fetchedAt`, `stale` and `unavailable[]`.
   The VIN is not a parameter; it comes from configuration.

   Generator `spring`, `interfaceOnly=true`, `delegatePattern=true`,
   `useSpringBoot4=true` (fall back to `useSpringBoot3` if unsupported),
   `openApiNullable=false`, `useTags=true`.

### 3.4 OpenAPI → MCP

Tools are generated from `skoda-service-api.yaml` in the same generator run via a
user-defined API template (`templateDir` + generator config `files:` entry,
`templateType: API`, `destinationFilename: McpTools.java`):

```java
@McpTool(name = "{{x-mcp-name}}",
         description = "{{summary}} {{notes}}",
         generateOutputSchema = true,
         annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
public {{returnType}} {{operationId}}() {
    return delegate.{{operationId}}().getBody();
}
```

Vendor extensions in our spec: `x-mcp-name`, `x-mcp-exclude` (e.g. if an endpoint
should be REST-only). The generated tools and the generated REST controllers both
call the same delegate beans → logic written once, descriptions defined once, and
everything is plain annotated Java at compile time (AOT/native friendly, no
runtime spec parsing).

Fallback if the template is impractical: a hand-written `SkodaMcpTools` calling
the delegates, plus a test asserting each `operationId` has a matching tool with
the spec's description.

### 3.5 Run modes

| | `mcp` (default) | `api` |
|---|---|---|
| `spring.main.web-application-type` | `none` | `servlet` |
| MCP stdio | on | off |
| REST + springdoc | off | on (`/api/v1/**`, `/swagger-ui.html`) |
| Logging | nothing on stdout (stdout = MCP protocol); optional file | console |
| Inbound auth | n/a | none (D6) |
| Actuator | off | `health` on management port |

The JAR runs either mode (`--spring.profiles.active=api`). Spring AOT fixes profiles
at build time, so the native build yields **two executables from the same
source**: `skoda-mcp` and `skoda-api` (`-Pnative,mcp` / `-Pnative,api`).

### 3.6 Authentication

- `SKODA_API_KEY` → `ClientHttpRequestInterceptor` adds `X-API-Key`.
- Interceptor captures `X-API-Key-Expires-At` and `RateLimit-*` into `ApiKeyStatus`;
  warns when expiry < 7 days (to stderr/file in `mcp` mode).
- 401 `api-key-expired` / 403 `api-key-not-authorized` → clear error telling the
  user to create or update the key in the MyŠkoda app.
- No other credentials anywhere.

### 3.7 Rate-limit-aware data access

- One `getVehicle` call fills a `VehicleSnapshot`; all tools/endpoints read from it.
- TTL configurable, default 5 min (`SKODA_CACHE_TTL`), single-flight.
- `RateLimit-Remaining` ≤ 2 → serve the cached snapshot with `stale=true` instead of calling.
- Upstream 429 → no retry; serve stale if available, otherwise error with `retryAfterSeconds`.
- Upstream 5xx/timeout → serve stale if available (marked), otherwise error.

### 3.8 Configuration

| Property | Env | Default / required |
|---|---|---|
| `skoda.vin` | `SKODA_VIN` | required |
| `skoda.public-api.key` | `SKODA_API_KEY` | required |
| `skoda.public-api.base-url` | `SKODA_PUBLIC_API_URL` | `https://public.api.connect.skoda-auto.cz` |
| `skoda.public-api.timeout` | – | `15s` |
| `skoda.cache.ttl` | `SKODA_CACHE_TTL` | `5m` |

Validated at startup (fail fast with a readable message). `.env` import kept
(`optional:file:.env[.properties]`); `.env.example` committed, `.env` ignored.

### 3.9 Native image

- Boot 4 AOT covers `@HttpExchange`, controllers and `@ConfigurationProperties`.
- Small `NativeHints` for Spring AI MCP reflective bits (e.g. `DefaultMetaProvider`)
  and tool return types if needed.
- `mvn -PnativeTest test` in CI.
- Root `Dockerfile`: GraalVM 25 builder → `debian:12-slim` runtime,
  `--build-arg BUILD_PROFILE=mcp|api`.
- Smoke checks: `skoda-mcp` answers `initialize` + `tools/list` over stdio;
  `skoda-api` answers `/actuator/health` and `/api/v1/vehicle` (against a stub).

## 4. Implementation steps

Each step is a commit; build and tests pass after each.

1. **Skeleton** – root `pom.xml` (Boot 4.1, Spring AI 2.0, Java 25), `SkodaApplication`,
   `mcp`/`api` profiles, `SkodaProperties`, `.gitignore`, `.env.example`.
2. **Upstream client** – vendor Skoda spec, generate client, `ApiKeyInterceptor`,
   `ApiKeyStatus`, error mapping. Tests: happy path, partial `errors[]`, 401/403/429/5xx.
3. **VehicleService + cache** – snapshot, TTL, single-flight, stale fallback,
   domain mapping. Tests incl. concurrency and rate-limit guard.
4. **Our contract + REST** – `skoda-service-api.yaml`, generated interfaces, delegates,
   bearer filter, ProblemDetail advice, springdoc, `@WebMvcTest`s.
5. **MCP** – custom template, generated tools, stdio config, tests (tool list ↔ spec,
   calls hit delegates), stdio smoke test with `McpSyncClient`.
6. **Native** – both native binaries, hints, `nativeTest`, Dockerfile, run both.
7. **Cleanup** – delete `api-skoda/`, `skoda-api-client/`, `skoda-mcp/`; rewrite
   `README.md` (getting an API key, config, MCP client config for JAR and native,
   Docker); CI workflow for build + test + native build (keep `notify-vps.yml`).

## 5. Risks

- **20 requests/hour**: mitigated by the snapshot cache and stale serving.
- **openapi-generator + Boot 4 / Jackson 3**: verify in step 2; avoid
  `jackson-databind-nullable`.
- **Native + Spring AI MCP**: may need a few extra hints, found via `nativeTest`
  and the stdio smoke test.
- **Public API is young (v1.1.0)**: spec is vendored; re-check the changelog when updating.

## 6. Implementation notes

Notable deviations from the plan above, and decisions made while implementing it:

| Area | Plan | Implemented |
|---|---|---|
| Versions | Boot 4.1.x, Spring AI 2.0, generator 7.20+ | Boot 4.1.1, Spring AI 2.0.1 (MCP Java SDK 2.0.0), openapi-generator 7.26.0, springdoc 3.1.1, GraalVM CE 25 |
| Our REST interfaces (3.3) | `delegatePattern=true` | **No delegate pattern.** With `interfaceOnly` the generator emits no `XxxApiDelegate`, and without it, it emits `@Controller` classes that would also load in the mcp profile. Instead: generated interfaces, thin `@Profile("api")` controllers, and a profile-independent `VehicleApiFacade` that both the controllers and the MCP tools call. |
| MCP tools (3.4) | one `McpTools` class | The API template renders once per tag, so there are **two classes**: `VehicleApiMcpTools` (4 tools) and `SystemApiMcpTools` (1). The tools are gated by `@ConditionalOnMcpServer` (`spring.ai.mcp.server.enabled`), not `@Profile("mcp")`, because `web-application-type: none` drops the *default* profile. Failures go through `McpToolErrors` and become `isError` results with retry advice. `McpToolsContractTest` guards the spec ↔ tool mapping. |
| DTO annotations | `annotationLibrary` not specified | **`annotationLibrary=swagger2`**. Spring AI derives the output schema from `@Schema`. Without it the schemas had no nullability, and the MCP server rejected results with `null` fields ("output validation failed"). |
| Upstream client (3.3) | generated `@HttpExchange` | As planned, but wired with an explicit `HttpServiceProxyFactory` instead of `@ImportHttpServices`. Reason: the `include` parameter is encoded as a single comma-separated value (`include=info,status,…`) with a literal comma (`URI_COMPONENT` encoding). |
| Error mapping | 401/403 → clear error | REST status codes: an expired, invalid or unauthorised API key → **503** (the operator must act, not the caller). Rate limit → 429 + `Retry-After`. Rejected VIN or invalid upstream response → 502. Outage → 503. Timeout → 504. An invalid key (other 401s) is a separate case. |
| Rate limiting (3.7) | as planned | Single-flight snapshot cache plus a guard (`RateLimit-Remaining ≤ 2` or a pending `Retry-After` → serve stale). No failure cooldown: during a 5xx outage, one upstream call per TTL. |
| Native (3.9) | `debian:12-slim` runtime | Builder `ghcr.io/graalvm/native-image-community:25-ol9`. OL9 has glibc 2.34; the default OL10 tag would not run on Debian 12. Runtime **`gcr.io/distroless/base-debian12:nonroot`**, `--static-nolibc`, `-march=compatibility`. The executables activate their AOT-frozen profile themselves. Images are about 185 MB (mcp) and 212 MB (api). `NativeHints` only needs the validated property records. |
| Tests | as planned | Native tests run in the Dockerfile `native-test` target. Mockito- and source-tree-based tests are disabled natively. `McpStdioServerIT` (Failsafe) exercises the packaged jar over stdio. `scripts/smoke-test-native.ps1` exercises both images against a Python stub. |
| stdio lifecycle | – | `spring.main.keep-alive` is **not** set. The SDK's stdio transport keeps the process alive on non-daemon threads and ends them when stdin reaches EOF, so the server exits with code 0 when the client closes stdin (and a `docker run -i --rm` container goes away). Covered by `McpStdioServerIT` and the smoke test. |
| Cleanup (step 7) | as planned | The legacy folders are deleted (still in the Git history). The README is rewritten, and `.github/workflows/ci.yml` runs the JVM build, both native images with smoke tests, and the native tests. `notify-vps.yml` is unchanged. |
