# syntax=docker/dockerfile:1.7

# Copy Maven rather than installing the Oracle Linux Maven package, which pulls
# an unnecessary second JDK and desktop-related dependencies into the builder.
FROM maven:3.9-eclipse-temurin-21 AS maven

# GraalVM 25 supports the reachability-metadata schema used by Spring Boot 4.1;
# Maven still compiles this project's configured Java 21 target. The final image
# contains only the compiled binary.
FROM ghcr.io/graalvm/native-image-community:25 AS builder

COPY --from=maven /usr/share/maven /usr/share/maven
ENV MAVEN_HOME=/usr/share/maven
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

# Spring's AOT engine evaluates profile-specific auto-configuration at build
# time. Select the mode this native executable is intended to run in.
ARG BUILD_PROFILE=mcp

WORKDIR /workspace

# skoda-api-client is an in-repository dependency and is not published to Maven
# Central. Build and install it in the builder before compiling this service.
COPY skoda-api-client/pom.xml skoda-api-client/skoda-api-server.yaml ./skoda-api-client/
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress --quiet -f skoda-api-client/pom.xml -DskipTests dependency:go-offline
COPY skoda-api-client/src ./skoda-api-client/src
RUN --mount=type=cache,target=/root/.m2 \
    cd skoda-api-client && \
    mvn --batch-mode --no-transfer-progress --quiet -DskipTests package && \
    mvn --batch-mode --no-transfer-progress --quiet org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
      -Dfile=target/skoda-api-client-2.1.0.jar \
      -DpomFile=pom.xml

# Keep dependency downloads cached between BuildKit builds while allowing source
# changes to reuse the resolved Maven dependencies.
COPY skoda-mcp/pom.xml ./skoda-mcp/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress --quiet -f skoda-mcp/pom.xml -DskipTests dependency:go-offline
COPY skoda-mcp/src ./skoda-mcp/src
RUN --mount=type=cache,target=/root/.m2 \
    SPRING_PROFILES_ACTIVE=${BUILD_PROFILE} \
    mvn --batch-mode --no-transfer-progress --quiet -f skoda-mcp/pom.xml -Pnative -DskipTests native:compile

# The generated executable is dynamically linked against glibc and zlib. Keep
# the runtime minimal while explicitly retaining the libraries it needs.
FROM debian:12-slim

# Keep the runtime profile aligned with the profile used for AOT processing.
ARG BUILD_PROFILE=mcp
ENV SPRING_PROFILES_ACTIVE=${BUILD_PROFILE}

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates zlib1g \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 65532 --create-home appuser

WORKDIR /app
COPY --from=builder --chown=appuser:appuser /workspace/skoda-mcp/target/skoda-mcp /app/skoda-mcp
USER appuser

# Used by an executable built with --build-arg BUILD_PROFILE=gateway.
EXPOSE 8090

ENTRYPOINT ["/app/skoda-mcp"]
