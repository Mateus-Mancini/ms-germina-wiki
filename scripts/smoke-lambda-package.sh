#!/usr/bin/env bash
# Smoke-tests the exact Lambda deployment zip before it is deployed (T040).
# Unit/integration tests run on the Maven classpath; this runs the unpacked zip, so a missing
# runtime jar fails here instead of in production.
#
# Usage: ./mvnw -Plambda -DskipTests clean package && scripts/smoke-lambda-package.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
zip="$root/target/wikigerminare-lambda.zip"
[[ -f "$zip" ]] || { echo "Missing $zip. Run: ./mvnw -Plambda -DskipTests clean package" >&2; exit 1; }

work="$(mktemp -d)"
container=""
cleanup() {
	[[ -n "$container" ]] && docker stop "$container" >/dev/null 2>&1 || true
	rm -rf "$work"
}
trap cleanup EXIT

unzip -q "$zip" -d "$work/pkg"

container="$(docker run -d --rm -e POSTGRES_PASSWORD=smoke -p 127.0.0.1::5432 postgres:18-alpine)"
port="$(docker port "$container" 5432/tcp | head -1 | awk -F: '{print $NF}')"
for _ in $(seq 1 30); do
	docker exec "$container" psql -h 127.0.0.1 -U postgres -tAc 'SELECT 1' >/dev/null 2>&1 && break
	sleep 1
done

# Use a fresh test-only signing key; the packaged app validates JWT settings at startup.
APP_AUTH_JWT_SECRET_BASE64="$(openssl rand -base64 32)" \
APP_AUTH_JWT_TTL_SECONDS=900
SPRING_PROFILES_ACTIVE=lambda \
SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${port}/postgres" \
SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD=smoke \
APP_STORAGE_ACCOUNT_ID=smoke APP_STORAGE_BUCKET=smoke APP_STORAGE_ACCESS_KEY_ID=smoke APP_STORAGE_SECRET_ACCESS_KEY=smoke \
	java -cp "$work/pkg:$work/pkg/lib/*" "$root/scripts/smoke/LambdaPackageSmoke.java"
