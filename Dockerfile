# syntax=docker/dockerfile:1.7
#
# Native executables of skoda-mcp-server (GraalVM 25) in a minimal runtime image.
#
#   docker build -t skoda-mcp .                                # stdio MCP server (default)
#   docker build -t skoda-api --build-arg BUILD_PROFILE=api .  # REST API
#   docker build --target native-test .                        # JVM + native tests (-PnativeTest)
#
# Spring AOT freezes the run mode at build time, so each run mode is its own executable and image
# (Maven profiles -Pnative,mcp / -Pnative,api); the executable activates its profile by itself.
# No secrets are baked in: SKODA_VIN and SKODA_API_KEY are passed at run time.

# mcp | api
ARG BUILD_PROFILE=mcp

# ---------------------------------------------------------------------------------------------
# GraalVM 25 native-image on Oracle Linux 9. OL9 (glibc 2.34) rather than the default OL10 tag
# (glibc 2.39), so the executable runs on the Debian 12 based runtime (glibc 2.36).
FROM ghcr.io/graalvm/native-image-community:25-ol9 AS source
ENV LANG=C.UTF-8
WORKDIR /workspace
# The wrapper downloads Maven (only-script wrapper; curl and tar are in the image). The cache mounts
# below keep Maven and the local repository between builds without putting them in a layer.
COPY --chmod=755 mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./
COPY src/ src/

# ---------------------------------------------------------------------------------------------
FROM source AS builder
ARG BUILD_PROFILE
# Link everything but glibc statically (zlib in particular): the distroless runtime has glibc but no
# zlib. Docker-specific, hence here and not in pom.xml (the option exists on Linux only).
ENV NATIVE_IMAGE_OPTIONS=--static-nolibc
# dist/ = the executable plus the JDK libraries native-image emits next to it (AWT/LCMS/management code
# is reachable through dependencies; such libraries are only loaded on demand, from the executable's
# directory). Tests run in the regular JVM build and in the native-test stage, not here.
RUN --mount=type=cache,target=/root/.m2 \
    case "${BUILD_PROFILE}" in mcp|api) ;; \
      *) echo "BUILD_PROFILE must be mcp or api, got '${BUILD_PROFILE}'" >&2; exit 1 ;; esac \
    && ./mvnw --batch-mode --no-transfer-progress -Pnative,${BUILD_PROFILE} -DskipTests package \
    && mkdir dist \
    && cp target/skoda-${BUILD_PROFILE} dist/ \
    && find target -maxdepth 1 -name '*.so' -exec cp {} dist/ \;

# ---------------------------------------------------------------------------------------------
# JVM tests, then the same tests compiled into a native test executable and run there
# (tests that need Mockito or the source tree are disabled in native mode, see the step-6 notes).
FROM source AS native-test
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress -PnativeTest test

# ---------------------------------------------------------------------------------------------
# Runtime: distroless base = glibc, CA certificates, tzdata and /tmp; no shell or package manager.
# Runs as the image's non-root user (uid 65532); the files are root-owned, i.e. read-only for the app.
FROM gcr.io/distroless/base-debian12:nonroot AS runtime
# Java derives the console encoding from the locale: keep stdout/stderr (MCP JSON, logs) in UTF-8.
ENV LANG=C.UTF-8
WORKDIR /app
COPY --from=builder /workspace/dist/ /app/
USER nonroot:nonroot

# stdio MCP server: `docker run -i --rm -e SKODA_VIN -e SKODA_API_KEY skoda-mcp`.
FROM runtime AS mcp
ENTRYPOINT ["/app/skoda-mcp"]

# REST API on 8080. Actuator health is on the management port 8081, deliberately not EXPOSEd:
# use it for container/orchestrator health checks and do not publish it.
FROM runtime AS api
EXPOSE 8080
ENTRYPOINT ["/app/skoda-api"]

# Final image = the stage named by BUILD_PROFILE.
FROM ${BUILD_PROFILE}
