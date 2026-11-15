#!/usr/bin/env bash
# What the original (maxrave-dev) did since we last looked.
#
#   scripts/upstream-news.sh          fetch both repos, list commits after the reviewed marks
#   scripts/upstream-news.sh --mark   move the marks to the current upstream heads (after review)
#
# Marks live in docs/upstream-reviewed.txt (committed). Nothing is merged: a fix we want is ported
# as its own task (see CLAUDE.md, «Оригинал»). Remotes are read-only (push URL DISABLED).
set -euo pipefail
cd "$(dirname "$0")/.."

MARKS=docs/upstream-reviewed.txt
MAIN_BRANCH=upstream/dev
CORE_BRANCH=upstream/multiplatform

ensure_remote() { # $1 = repo dir, $2 = url
    if ! git -C "$1" remote get-url upstream >/dev/null 2>&1; then
        git -C "$1" remote add upstream "$2"
        git -C "$1" remote set-url --push upstream DISABLED
    fi
}
ensure_remote . https://github.com/maxrave-dev/SimpMusic.git
ensure_remote core https://github.com/maxrave-dev/core
git fetch -q upstream
git -C core fetch -q upstream

main_mark=$(awk '$1 == "main" { print $2 }' "$MARKS")
core_mark=$(awk '$1 == "core" { print $2 }' "$MARKS")

if [[ "${1:-}" == "--mark" ]]; then
    printf 'main %s\ncore %s\n' "$(git rev-parse --short "$MAIN_BRANCH")" \
        "$(git -C core rev-parse --short "$CORE_BRANCH")" > "$MARKS"
    echo "Marks moved:"; cat "$MARKS"
    exit 0
fi

show() { # $1 = repo dir, $2 = range, $3 = title
    local n
    n=$(git -C "$1" rev-list --no-merges --count "$2")
    echo "== $3: $n new ($2)"
    [[ $n -eq 0 ]] && return
    git -C "$1" log --no-merges --reverse --date=short --format='%h %ad %s' "$2"
    echo
}

show . "$main_mark..$MAIN_BRANCH" "SimpMusic"
show core "$core_mark..$CORE_BRANCH" "core"

# The part that keeps sound and sign-in alive: scraper, stream extraction, extractor versions.
echo "== Critical (scraper, stream, extractors)"
git -C core log --no-merges --date=short --format='core %h %ad %s' "$core_mark..$CORE_BRANCH" -- \
    service/kotlinYtmusicScraper data/src/commonMain/kotlin/com/maxrave/data/repository/StreamRepositoryImpl.kt
git log --no-merges --date=short --format='main %h %ad %s' "$main_mark..$MAIN_BRANCH" -G 'newpipe|pipepipe|quickjs' \
    -- gradle/libs.versions.toml
