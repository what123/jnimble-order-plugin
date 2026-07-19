#!/usr/bin/env bash
#
# Apply the "dev-classpath" local patches to the jnimble-framework submodule.
#
# Why this exists:
#   To enable "clone & run" development, the starter needs two local changes
#   in the framework submodule:
#     1. jnimble-starter/pom.xml                          -> add business plugin dependencies
#     2. jnimble-starter/src/main/resources/application.yml -> dev-classpath-enabled=true
#   These changes are NOT pushed to the upstream framework repo (what123/JNimble),
#   so clone-time users must apply them locally via this script.
#
# Usage:
#   bash scripts/apply-dev-classpath.sh           # apply
#   bash scripts/apply-dev-classpath.sh --check   # only check if applied
#
# Re-run is safe: if already applied, the script reports "already applied" and exits 0.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FRAMEWORK_DIR="$REPO_ROOT/jnimble-framework"
PATCH_FILE="$SCRIPT_DIR/dev-classpath.patch"

if [[ ! -e "$FRAMEWORK_DIR/.git" ]]; then
  echo "ERROR: jnimble-framework submodule not found at $FRAMEWORK_DIR" >&2
  echo "Run: git submodule update --init --recursive" >&2
  exit 1
fi

if [[ ! -f "$PATCH_FILE" ]]; then
  echo "ERROR: patch file not found: $PATCH_FILE" >&2
  exit 1
fi

# Detect "submodule .git is a file" case (submodule uses .git file pointer)
cd "$FRAMEWORK_DIR"

# Check mode: just verify whether the patch is already applied
if [[ "${1:-}" == "--check" ]]; then
  if git diff --quiet -- jnimble-starter/pom.xml jnimble-starter/src/main/resources/application.yml; then
    echo "not-applied"
    exit 0
  else
    echo "applied"
    exit 0
  fi
fi

# Check if already applied (idempotent)
if ! git diff --quiet -- jnimble-starter/pom.xml jnimble-starter/src/main/resources/application.yml; then
  echo "dev-classpath patch already applied (working tree has changes)."
  exit 0
fi

# Apply the patch
echo "Applying dev-classpath patch to jnimble-framework..."
if git apply --whitespace=nowarn "$PATCH_FILE"; then
  echo "Done. Run:"
  echo "  cd jnimble-framework && mvn -pl jnimble-starter spring-boot:run"
else
  echo "ERROR: failed to apply patch. The submodule may have diverged from the patch baseline." >&2
  echo "Inspect $PATCH_FILE and apply manually." >&2
  exit 1
fi
