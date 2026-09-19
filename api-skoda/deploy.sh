#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

IMAGE="${IMAGE:-ghcr.io/georgedanicico/api-skoda:latest}"
CONTAINER_NAME="${CONTAINER_NAME:-api-skoda}"
HOST_ADDRESS="${HOST_ADDRESS:-127.0.0.1}"
APP_PORT="${APP_PORT:-8090}"
MANAGEMENT_PORT="${MANAGEMENT_PORT:-8888}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-60}"
BUILD_IMAGE=0
PULL_IMAGE=1
ENV_FILE=""

usage() {
    cat <<'EOF'
Usage: ./deploy.sh [options]

Pull and run the published Skoda API native Docker image.

Options:
  --image IMAGE             Docker image name and tag (default: ghcr.io/georgedanicico/api-skoda:latest)
  --container NAME         Container name (default: api-skoda)
  --host-address ADDRESS   Host bind address (default: 127.0.0.1)
  --app-port PORT          Host port for the application API (default: 8080)
  --management-port PORT   Host port for health and metrics (default: 8888)
  --env-file FILE          Optional Docker environment file
  --build                  Build the image locally instead of pulling it
  --no-pull                Run the existing local image without pulling it
  -h, --help               Show this help

Environment variables can also be used for the corresponding options:
IMAGE, CONTAINER_NAME, HOST_ADDRESS, APP_PORT, MANAGEMENT_PORT, HEALTH_TIMEOUT.
EOF
}

die() {
    echo "deploy.sh: $*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

while (($# > 0)); do
    case "$1" in
        --image)
            (($# >= 2)) || die "--image requires a value"
            IMAGE="$2"
            shift 2
            ;;
        --container)
            (($# >= 2)) || die "--container requires a value"
            CONTAINER_NAME="$2"
            shift 2
            ;;
        --host-address)
            (($# >= 2)) || die "--host-address requires a value"
            HOST_ADDRESS="$2"
            shift 2
            ;;
        --app-port)
            (($# >= 2)) || die "--app-port requires a value"
            APP_PORT="$2"
            shift 2
            ;;
        --management-port)
            (($# >= 2)) || die "--management-port requires a value"
            MANAGEMENT_PORT="$2"
            shift 2
            ;;
        --env-file)
            (($# >= 2)) || die "--env-file requires a value"
            ENV_FILE="$2"
            shift 2
            ;;
        --build)
            BUILD_IMAGE=1
            PULL_IMAGE=0
            shift
            ;;
        --no-pull)
            PULL_IMAGE=0
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            die "unknown option: $1 (use --help for usage)"
            ;;
    esac
done

require_command docker
require_command curl

[[ "$APP_PORT" =~ ^[0-9]+$ ]] || die "application port must be numeric: $APP_PORT"
[[ "$MANAGEMENT_PORT" =~ ^[0-9]+$ ]] || die "management port must be numeric: $MANAGEMENT_PORT"
[[ "$HEALTH_TIMEOUT" =~ ^[0-9]+$ ]] || die "health timeout must be numeric: $HEALTH_TIMEOUT"

if [[ -n "$ENV_FILE" ]]; then
    [[ -f "$ENV_FILE" ]] || die "environment file does not exist: $ENV_FILE"
fi

if ((BUILD_IMAGE)); then
    echo "Building $IMAGE"
    DOCKER_BUILDKIT=1 docker build \
        --file "$SCRIPT_DIR/Dockerfile" \
        --tag "$IMAGE" \
        "$SCRIPT_DIR"
elif ((PULL_IMAGE)); then
    echo "Pulling $IMAGE"
    docker pull "$IMAGE"
else
    docker image inspect "$IMAGE" >/dev/null 2>&1 || die "Docker image does not exist: $IMAGE"
fi

if docker container inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
    echo "Removing existing container $CONTAINER_NAME"
    docker rm --force "$CONTAINER_NAME" >/dev/null
fi

run_args=(
    --detach
    --init
    --name "$CONTAINER_NAME"
    --publish "$HOST_ADDRESS:$APP_PORT:8080"
    --publish "$HOST_ADDRESS:$MANAGEMENT_PORT:8888"
)

if [[ -n "$ENV_FILE" ]]; then
    run_args+=(--env-file "$ENV_FILE")
fi

echo "Starting $CONTAINER_NAME"
docker run "${run_args[@]}" "$IMAGE"

health_url="http://$HOST_ADDRESS:$MANAGEMENT_PORT/health"
deadline=$((SECONDS + HEALTH_TIMEOUT))
until curl --fail --silent --show-error "$health_url" >/dev/null; do
    if ((SECONDS >= deadline)); then
        echo "Application did not become healthy within ${HEALTH_TIMEOUT}s" >&2
        docker logs "$CONTAINER_NAME" >&2 || true
        exit 1
    fi

    if [[ "$(docker inspect --format '{{.State.Status}}' "$CONTAINER_NAME")" == "exited" ]]; then
        echo "Container exited before becoming healthy" >&2
        docker logs "$CONTAINER_NAME" >&2 || true
        exit 1
    fi

    sleep 2
done

echo "Application is healthy"
echo "API:        http://$HOST_ADDRESS:$APP_PORT"
echo "Health:     $health_url"
echo "Container:  $CONTAINER_NAME"
