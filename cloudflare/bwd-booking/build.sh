#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HERE/../.." && pwd)"
PUBLIC="$HERE/public"

rm -rf "$PUBLIC"
mkdir -p "$PUBLIC"
cp -a "$REPO_ROOT/backend/bwd-cloud-v2/public/." "$PUBLIC/"

node --check "$HERE/src/index.js"
python3 -m json.tool "$HERE/wrangler.jsonc" >/dev/null
grep -q 'BOOK YOUR DATE' "$PUBLIC/index.html"
grep -q 'No app. No account.' "$PUBLIC/index.html"
grep -q '/booking/' "$PUBLIC/index.html"

echo "BWD Cloudflare web build: PASS"
echo "Static source: backend/bwd-cloud-v2/public"
echo "Deploy config: cloudflare/bwd-booking/wrangler.jsonc"
