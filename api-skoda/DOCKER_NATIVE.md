# Native Docker image

This project can be built as a GraalVM native executable and packaged in a
small Debian runtime image. The Dockerfile does not contain MySkoda
credentials or any other secrets.

## Build

From this directory:

```bash
DOCKER_BUILDKIT=1 docker build \
  --file Dockerfile \
  --tag api-skoda:native \
  .
```

The first build downloads Maven dependencies and compiles the native image, so
it can take several minutes. Subsequent builds reuse the BuildKit Maven cache.

## Run locally

Publish the ports only on loopback for local testing:

```bash
docker run --rm --init \
  --name api-skoda-native \
  --publish 127.0.0.1:8080:8080 \
  --publish 127.0.0.1:8888:8888 \
  api-skoda:native
```

The application API is on port `8080`; health and Prometheus endpoints are on
port `8888`.

## HTTP examples

The `/ping` endpoint does not call MySkoda and is a safe container smoke test:

```bash
curl --include http://127.0.0.1:8080/ping
curl --fail --silent http://127.0.0.1:8888/actuator/health
```

For upstream calls, provide a valid MySkoda bearer token and VIN. The
application forwards the incoming `Authorization` header to the upstream
MySkoda API; do not put the token in the image or commit it to a file.

```bash
export SKODA_AUTHORIZATION='Bearer <my-skoda-access-token>'
export VIN='<vehicle-vin>'
```

Read-only examples:

```bash
curl --fail --silent \
  --header "Authorization: ${SKODA_AUTHORIZATION}" \
  "http://127.0.0.1:8080/vehicle"

curl --fail --silent \
  --header "Authorization: ${SKODA_AUTHORIZATION}" \
  "http://127.0.0.1:8080/status/${VIN}"

curl --fail --silent \
  --header "Authorization: ${SKODA_AUTHORIZATION}" \
  "http://127.0.0.1:8080/range/${VIN}"

curl --fail --silent \
  --header "Authorization: ${SKODA_AUTHORIZATION}" \
  "http://127.0.0.1:8080/location/${VIN}"

curl --fail --silent \
  --header "Authorization: ${SKODA_AUTHORIZATION}" \
  "http://127.0.0.1:8080/charging/${VIN}"

curl --fail --silent \
  --header "Authorization: ${SKODA_AUTHORIZATION}" \
  "http://127.0.0.1:8080/air-conditioning/${VIN}"
```

The following calls control the vehicle. Run them only when you intend to
send the command:

```bash
# Start air conditioning.
curl --include --request POST \
  --header "Authorization: ${SKODA_AUTHORIZATION}" \
  --header 'Content-Type: application/json' \
  --data '{"heaterSource":"ELECTRIC","temperature":21.5,"temperatureUnit":"CELSIUS"}' \
  "http://127.0.0.1:8080/air-conditioning/${VIN}/start"

# Start or stop charging.
curl --include --request POST \
  --header "Authorization: ${SKODA_AUTHORIZATION}" \
  "http://127.0.0.1:8080/charging/${VIN}/start"

curl --include --request POST \
  --header "Authorization: ${SKODA_AUTHORIZATION}" \
  "http://127.0.0.1:8080/charging/${VIN}/stop"

# Lock or unlock; replace 1234 with the vehicle's four-digit S-PIN.
curl --include --request POST \
  --header "Authorization: ${SKODA_AUTHORIZATION}" \
  --header 'Content-Type: application/json' \
  --data '{"spin":"1234"}' \
  "http://127.0.0.1:8080/vehicle-access/${VIN}/lock"

curl --include --request POST \
  --header "Authorization: ${SKODA_AUTHORIZATION}" \
  --header 'Content-Type: application/json' \
  --data '{"spin":"1234"}' \
  "http://127.0.0.1:8080/vehicle-access/${VIN}/unlock"
```

The API also exposes `POST /air-conditioning/{vin}/stop`,
`GET /charging/{vin}/session`, `POST /vehicle-access/{vin}/flash`, and
`POST /vehicle-access/{vin}/honk-and-flash`.
