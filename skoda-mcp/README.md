# skoda-mcp

## Native Docker image

Build the GraalVM native image for the default MCP stdio server:

```bash
docker build -f skoda-mcp/Dockerfile -t skoda-mcp:native .
```

Run the default MCP stdio server, supplying secrets from the local `.env` file:

```bash
docker run --rm -i --env-file skoda-mcp/.env skoda-mcp:native
```

Build a separate native image for the HTTP gateway, then run it with the same
profile:

```bash
docker build -f skoda-mcp/Dockerfile \
  --build-arg BUILD_PROFILE=gateway \
  -t skoda-mcp:gateway-native .

docker run --rm -p 8090:8090 --env-file skoda-mcp/.env \
  --add-host host.docker.internal:host-gateway \
  -e SKODA_API_BASE_URL=http://host.docker.internal:8080 \
  skoda-mcp:gateway-native
```

For host-only access, publish the port only on loopback instead:

```bash
docker run --rm -p 127.0.0.1:8090:8090 --env-file skoda-mcp/.env \
  --add-host host.docker.internal:host-gateway \
  -e SKODA_API_BASE_URL=http://host.docker.internal:8080 \
  skoda-mcp:gateway-native
```

### Vehicle API connectivity

The gateway sends vehicle requests to `api-skoda`; it does not embed that
service. The default URL, `http://127.0.0.1:8080`, works for a JVM process on
the host but points back to the MCP container when Docker is used. Start the
local API service first:

```bash
cd api-skoda
mvn spring-boot:run
```

Then use the `--add-host` and `SKODA_API_BASE_URL` options shown above. If
`api-skoda` runs in another container on the same Docker network, use its
service name instead, for example `SKODA_API_BASE_URL=http://api-skoda:8080`.

Run the build command from the repository root (`skoda-mcp-server`). It builds
the in-repository `skoda-api-client` dependency first. The Docker build context
excludes `.env` and `.env.local`; secrets are supplied only at container runtime.
Spring AOT evaluates profile-specific configuration during the native build, so
do not override the profile baked into an MCP- or gateway-native image.
