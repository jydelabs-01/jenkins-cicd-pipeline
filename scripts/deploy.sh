#!/usr/bin/env bash
# Simulated deployment of fee-service to a "test environment" (a directory + background process
# on the Jenkins host), followed by smoke tests. Fails non-zero if anything is wrong.
set -euo pipefail

JAR="${1:?usage: deploy.sh <path-to-jar>}"
DEPLOY_DIR="${DEPLOY_DIR:-/tmp/deployments/test/fee-service}"
APP_PORT="${APP_PORT:-8085}"
BUILD_NUMBER="${BUILD_NUMBER:-local}"
PID_FILE="$DEPLOY_DIR/app.pid"
LOG_FILE="$DEPLOY_DIR/app.log"
BASE_URL="http://localhost:${APP_PORT}"

[ -f "$JAR" ] || { echo "ERROR: artifact $JAR not found"; exit 1; }

# The deploy token proves a credential was injected from Jenkins. Never print it.
if [ -z "${DEPLOY_TOKEN:-}" ]; then
  echo "ERROR: DEPLOY_TOKEN not provided (expected from Jenkins credential 'test-env-deploy-token')"
  exit 1
fi
echo "Deploy token received (${#DEPLOY_TOKEN} chars, value masked)"

mkdir -p "$DEPLOY_DIR/releases"

# 1. Stop the currently running version, if any
if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "Stopping previous instance (pid $(cat "$PID_FILE"))"
  kill "$(cat "$PID_FILE")" || true
  for _ in $(seq 1 10); do kill -0 "$(cat "$PID_FILE")" 2>/dev/null || break; sleep 1; done
fi

# 2. Install the new release and point "current" at it
cp "$JAR" "$DEPLOY_DIR/releases/fee-service-${BUILD_NUMBER}.jar"
ln -sfn "$DEPLOY_DIR/releases/fee-service-${BUILD_NUMBER}.jar" "$DEPLOY_DIR/current.jar"
echo "Installed release fee-service-${BUILD_NUMBER}.jar"

# 3. Start it in the background
APP_PORT="$APP_PORT" nohup java -jar "$DEPLOY_DIR/current.jar" > "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"
echo "Started fee-service (pid $(cat "$PID_FILE")) on port $APP_PORT"

# 4. Wait for health
for i in $(seq 1 30); do
  if curl -fsS "$BASE_URL/health" >/dev/null 2>&1; then
    echo "Health check passed after ${i}s: $(curl -fsS "$BASE_URL/health")"
    break
  fi
  if [ "$i" -eq 30 ]; then
    echo "ERROR: service did not become healthy in 30s. Last log lines:"; tail -20 "$LOG_FILE"; exit 1
  fi
  sleep 1
done

# 5. Smoke test a real endpoint
RESPONSE="$(curl -fsS "$BASE_URL/api/fee?amount=1000")"
echo "Smoke test /api/fee?amount=1000 -> $RESPONSE"
echo "$RESPONSE" | grep -q '"fee":"10.75"' || { echo "ERROR: unexpected smoke test response"; exit 1; }

# 6. Keep the last 5 releases
ls -1t "$DEPLOY_DIR"/releases/*.jar | tail -n +6 | xargs -r rm -f

echo "Deployment of build ${BUILD_NUMBER} to TEST succeeded"
