# syntax=docker/dockerfile:1.7

# Maven is copied into the GraalVM builder so the build uses Java 21 and
# native-image while retaining Maven's standard dependency resolution.
FROM maven:3.9-eclipse-temurin-21 AS maven

FROM ghcr.io/graalvm/native-image-community:21 AS builder

COPY --from=maven /usr/share/maven /usr/share/maven
ENV MAVEN_HOME=/usr/share/maven
ENV PATH="${MAVEN_HOME}/bin:${PATH}"

WORKDIR /workspace

# Keep dependency resolution cached independently from application sources.
# The OpenAPI document is needed by the generator during the Maven build.
COPY pom.xml skoda-api-server.yaml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress --quiet -DskipTests dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress --quiet -Pnative -DskipTests native:compile

FROM debian:12-slim AS runtime

RUN apt-get update \
    && apt-get install --yes --no-install-recommends ca-certificates zlib1g \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 65532 --create-home appuser

WORKDIR /app
COPY --from=builder --chown=appuser:appuser /workspace/target/api /app/api-skoda

ENV SERVER_ADDRESS=0.0.0.0

USER appuser
EXPOSE 8080 8888
ENTRYPOINT ["/app/api-skoda"]
