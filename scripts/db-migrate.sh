#!/usr/bin/env bash
# Applies Flyway migrations (src/main/resources/db/migration) to the Neon database.
# Contract: specs/002-database-migrations/contracts/migrations.md
#
#   scripts/db-migrate.sh rehearse     any branch: migrate a disposable Neon branch of production, then delete it
#   scripts/db-migrate.sh production   main only:  migrate production (asks for 'yes'; DB_MIGRATE_CONFIRM=yes for CI)
#   scripts/db-migrate.sh info         any branch: read-only status of production
#
# Needs DB_URL (pooled JDBC URL), DB_USER, DB_PASS and, for rehearse, NEON_PROJECT_ID (e.g. from
# ~/.config/germinawiki/prod.env). Credentials reach Flyway through FLYWAY_* environment variables,
# never command-line arguments, and are never printed.
set -euo pipefail

cd "$(dirname "$0")/.."

die() { echo "db-migrate: $*" >&2; exit 1; }
require() { for v in "$@"; do [[ -n "${!v:-}" ]] || die "$v is not set (source your secrets file)"; done; }

# Neon's pooler (PgBouncer, transaction mode) breaks Flyway's session lock: migrate through the direct endpoint.
direct_url() { echo "${DB_URL/-pooler/}"; }

info() { FLYWAY_URL="$1" FLYWAY_USER="$DB_USER" FLYWAY_PASSWORD="$DB_PASS" ./mvnw -B -ntp flyway:info 2>&1 |
	grep -E '^(\[INFO\] )?(Schema version|Database|\+-|\| )|ERROR' | sed 's/^\[INFO\] //'; }

migrate() { FLYWAY_URL="$1" FLYWAY_USER="$DB_USER" FLYWAY_PASSWORD="$DB_PASS" ./mvnw -B -ntp flyway:migrate 2>&1 |
	grep -E 'Migrating schema|Successfully (applied|validated)|up to date|ERROR|FAILURE' | sed 's/^\[INFO\] //'; }

rehearse() {
	require DB_URL DB_USER DB_PASS NEON_PROJECT_ID
	local git_branch expires host
	git_branch="$(git rev-parse --abbrev-ref HEAD | tr -c 'a-zA-Z0-9-' '-' | cut -c1-30)"
	# Global (not local): the EXIT trap runs after this function returns.
	REHEARSAL_BRANCH="rehearse-${git_branch%-}-$(date -u +%Y%m%d%H%M%S)"
	local name="$REHEARSAL_BRANCH"
	expires="$(date -u -d '+1 hour' +%Y-%m-%dT%H:%M:%SZ)"

	echo "Creating disposable Neon branch '$name' from the default (production) branch, expires $expires"
	npx --yes neonctl branches create --project-id "$NEON_PROJECT_ID" --name "$name" --expires-at "$expires" \
		--output json >/dev/null
	trap 'echo "Deleting $REHEARSAL_BRANCH"; npx --yes neonctl branches delete "$REHEARSAL_BRANCH" --project-id "$NEON_PROJECT_ID" >/dev/null' EXIT

	host="$(npx --yes neonctl connection-string "$name" --project-id "$NEON_PROJECT_ID" --role-name "$DB_USER" 2>/dev/null |
		python3 -c 'import sys, urllib.parse as u; print(u.urlparse(sys.stdin.read().strip()).hostname)')"
	[[ -n "$host" && "$host" != *-pooler* ]] || die "could not resolve the branch's direct endpoint"
	local url="jdbc:postgresql://${host}/${DB_URL##*/}"

	echo "--- before"; info "$url"
	echo "--- migrate"; migrate "$url"
	echo "--- after"; info "$url"
}

production() {
	require DB_URL DB_USER DB_PASS
	[[ "$(git rev-parse --abbrev-ref HEAD)" == "main" ]] || die "production migrations run only from 'main' (merged, reviewed migrations)"
	[[ -z "$(git status --porcelain)" ]] || die "working tree is not clean"
	git fetch -q origin main
	[[ "$(git rev-parse HEAD)" == "$(git rev-parse origin/main)" ]] || die "local main differs from origin/main (git pull --ff-only)"

	local url; url="$(direct_url)"
	echo "--- production status"; info "$url"
	if [[ "${DB_MIGRATE_CONFIRM:-}" != "yes" ]]; then
		read -r -p "Apply pending migrations to PRODUCTION? Type 'yes': " answer
		[[ "$answer" == "yes" ]] || die "aborted"
	fi
	echo "--- migrate"; migrate "$url"
	echo "--- after"; info "$url"
}

case "${1:-}" in
	rehearse) rehearse ;;
	production) production ;;
	info) require DB_URL DB_USER DB_PASS; info "$(direct_url)" ;;
	*) die "usage: $0 rehearse|production|info" ;;
esac
