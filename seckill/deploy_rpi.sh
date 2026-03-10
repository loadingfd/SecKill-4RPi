#!/usr/bin/env bash
set -euo pipefail

# Minimal deploy script: only APP_ONLY flow
# - Assumes you run it from the project root (or set TARGET_DIR)
# - Builds the jar using a containerized JDK/Maven (no local JDK required)
# - Runs the app in a container using host network so it can access services on the Pi

TARGET_DIR=${TARGET_DIR:-$(pwd)}
DOCKER_PLATFORM=${DOCKER_PLATFORM:-}
SERVER_PORT=${SERVER_PORT:-8080}
PC_HOST=${PC_HOST:-localhost}

info(){ echo -e "[INFO] $*"; }
error(){ echo -e "[ERROR] $*" >&2; }

# detect architecture and set DOCKER_PLATFORM if not provided
ARCH=$(uname -m)
case "$ARCH" in
  aarch64|arm64)
    DOCKER_PLATFORM=${DOCKER_PLATFORM:-linux/arm64}
    ;;
  armv7l|armv6l)
    DOCKER_PLATFORM=${DOCKER_PLATFORM:-linux/arm/v7}
    ;;
  x86_64)
    DOCKER_PLATFORM=${DOCKER_PLATFORM:-linux/amd64}
    ;;
  *)
    error "Unsupported architecture: $ARCH"; exit 1;
    ;;
esac
info "Detected arch=$ARCH, using DOCKER_PLATFORM=$DOCKER_PLATFORM"

# Ensure Docker exists; if not, try to install (requires sudo)
if ! command -v docker >/dev/null 2>&1; then
  info "Docker not found — attempting to install (requires sudo)"
  if [ "$EUID" -ne 0 ]; then
    SUDO=sudo
  else
    SUDO=
  fi
  $SUDO apt-get update
  $SUDO apt-get install -y ca-certificates curl gnupg lsb-release
  $SUDO mkdir -p /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/debian/gpg | $SUDO gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian \
  $(lsb_release -cs) stable" | $SUDO tee /etc/apt/sources.list.d/docker.list > /dev/null
  $SUDO apt-get update
  $SUDO apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
  $SUDO systemctl enable --now docker || true
  if [ -n "${SUDO_USER:-}" ]; then
    $SUDO usermod -aG docker ${SUDO_USER} || true
  fi
  info "Docker installed"
else
  info "Docker already installed"
fi

cd "$TARGET_DIR"

# Build jar using containerized JDK or Maven
if [ -f mvnw ]; then
  info "Packaging project using containerized JDK and ./mvnw"
  docker run --rm -v "$TARGET_DIR":/workspace -w /workspace --platform "$DOCKER_PLATFORM" eclipse-temurin:21-jdk bash -lc "./mvnw -DskipTests package -T1C"
else
  info "No mvnw found, using maven image to build"
  docker run --rm -v "$TARGET_DIR":/workspace -w /workspace --platform "$DOCKER_PLATFORM" maven:3.9-jdk-21 bash -lc "mvn -DskipTests package -T1C"
fi

# locate jar
JAR_PATH=$(find target -maxdepth 2 -type f -name "seckill-*.jar" | head -n1 || true)
if [ -z "$JAR_PATH" ]; then
  error "Jar not found under target/. Build may have failed."; exit 1
fi
info "Built jar: $JAR_PATH"

# prepare deploy dir and copy jar
DEPLOY_DIR="$TARGET_DIR/deploy"
mkdir -p "$DEPLOY_DIR"
cp "$JAR_PATH" "$DEPLOY_DIR/app.jar"

# remove existing container if present
if docker ps -a --format '{{.Names}}' | grep -q '^seckill_app$'; then
  info "Removing existing seckill_app container"
  docker rm -f seckill_app || true
fi

# run container with host network so it can access host services
info "Starting application container (name=seckill_app) with host network"
docker run -d --name seckill_app --platform "$DOCKER_PLATFORM" --network host \
  -v "$DEPLOY_DIR/app.jar":/app/app.jar \
  -e PC_HOST="$PC_HOST" \
  -e SERVER_PORT="$SERVER_PORT" \
  eclipse-temurin:21-jre java -jar /app/app.jar

# wait for health
info "Waiting for application to become healthy (http://localhost:${SERVER_PORT}/actuator/health)"
TRIES=0
until curl -sSf "http://localhost:${SERVER_PORT}/actuator/health" >/dev/null 2>&1 || [ "$TRIES" -ge 40 ]; do
  TRIES=$((TRIES+1))
  echo -n "."
  sleep 3
done
if [ "$TRIES" -ge 40 ]; then
  error "Application health check failed after timeout. Check container logs: docker logs seckill_app"; exit 1
fi

info "Application is up on port ${SERVER_PORT}!"
info "Logs: docker logs -f seckill_app"

cat <<EOF
Done.
- 查看容器: docker ps --filter name=seckill_app
- 日志: docker logs -f seckill_app
EOF

