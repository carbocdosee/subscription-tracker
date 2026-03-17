#!/usr/bin/env bash
# run-tests.sh — Start Docker, run API tests, generate HTML report, shut down.
#
# Usage:
#   ./run-tests.sh            # normal run
#   ./run-tests.sh --build    # force Docker image rebuild first
#   ./run-tests.sh --keep-up  # leave the stack running after tests

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_TESTS="$SCRIPT_DIR/api-tests"
REPORT_FILE="$API_TESTS/reports/test-report.html"

BUILD=false
KEEP_UP=false
TEST_EXIT=0

for arg in "$@"; do
    case $arg in
        --build)   BUILD=true  ;;
        --keep-up) KEEP_UP=true ;;
    esac
done

# ── Console helpers ────────────────────────────────────────────────────────────
step()    { printf "\n\033[36m▶  %s\033[0m\n" "$*"; }
ok()      { printf "\033[32m✔  %s\033[0m\n" "$*"; }
warn()    { printf "\033[33m⚠  %s\033[0m\n" "$*"; }
fail()    { printf "\033[31m✖  %s\033[0m\n" "$*" >&2; }

# ── Guaranteed cleanup on EXIT ─────────────────────────────────────────────────
cleanup() {
    cd "$SCRIPT_DIR"
    if [ "$KEEP_UP" = false ]; then
        step 'Shutting down Docker Compose stack...'
        docker compose down 2>/dev/null || true
        ok 'Stack stopped.'
    else
        warn 'Stack left running (--keep-up). Stop it manually: docker compose down'
    fi

    # Open report if it exists
    if [ -f "$REPORT_FILE" ]; then
        step "Test report: $REPORT_FILE"
        if command -v xdg-open &>/dev/null; then
            xdg-open "$REPORT_FILE" &
        elif command -v open &>/dev/null; then
            open "$REPORT_FILE" &
        fi
    else
        warn "Report file not found at: $REPORT_FILE"
    fi

    # Final summary
    echo ''
    if [ "$TEST_EXIT" -eq 0 ]; then
        ok 'All tests PASSED.'
    else
        fail "Some tests FAILED (exit $TEST_EXIT). See the HTML report for details."
    fi
}
trap cleanup EXIT

# ── 1. Start Docker Compose ────────────────────────────────────────────────────
step 'Starting Docker Compose stack...'
cd "$SCRIPT_DIR"
COMPOSE_ARGS=('compose' 'up' '-d')
[ "$BUILD" = true ] && COMPOSE_ARGS+=('--build')
docker "${COMPOSE_ARGS[@]}"

# ── 2. Wait for backend health ─────────────────────────────────────────────────
step 'Waiting for backend to become healthy...'
HEALTH_URL='http://localhost:80/health'
MAX_WAIT=120
INTERVAL=5
ELAPSED=0

while true; do
    sleep "$INTERVAL"
    ELAPSED=$((ELAPSED + INTERVAL))

    if curl -sf --max-time 3 "$HEALTH_URL" > /dev/null 2>&1; then
        ok "Backend is healthy after ${ELAPSED}s."
        break
    fi

    printf "   ...waiting (%d/%ds)\n" "$ELAPSED" "$MAX_WAIT"

    if [ "$ELAPSED" -ge "$MAX_WAIT" ]; then
        fail "Backend did not become healthy within ${MAX_WAIT}s."
        echo '   Hint: docker compose logs backend'
        exit 1
    fi
done

# ── 3. Prepare api-tests ───────────────────────────────────────────────────────
step 'Preparing api-tests...'
cd "$API_TESTS"

ENV_FILE="$API_TESTS/.env"
ENV_EXAMPLE="$API_TESTS/.env.test.example"
if [ ! -f "$ENV_FILE" ]; then
    cp "$ENV_EXAMPLE" "$ENV_FILE"
    ok 'Created api-tests/.env from .env.test.example'
fi

step 'Installing npm dependencies...'
npm install --silent

# ── 4. Run tests ───────────────────────────────────────────────────────────────
step 'Running API tests...'
npm run test:ci || TEST_EXIT=$?

cd "$SCRIPT_DIR"
exit "$TEST_EXIT"
