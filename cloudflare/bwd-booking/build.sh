#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HERE/../.." && pwd)"
PUBLIC="$HERE/public"

rm -rf "$PUBLIC"
mkdir -p "$PUBLIC"
cp -a "$REPO_ROOT/backend/bwd-cloud-v2/public/." "$PUBLIC/"

python3 - <<'PY'
from pathlib import Path
p = Path('cloudflare/bwd-booking/public/index.html')
s = p.read_text()
old = "https://images.unsplash.com/photo-1693576588167-2e7148490dc5?auto=format&fit=crop&w=1800&q=88"
new = "https://images.unsplash.com/photo-1618107095181-e3ba0f53ee59?auto=format&fit=crop&w=1800&q=88"
if old not in s:
    raise SystemExit('hero image anchor not found')
s = s.replace(old, new, 1)
s = s.replace("background-position:58% center;transform:scale(1.015)", "background-position:center center;transform:scale(1.02);filter:saturate(.92) contrast(1.08)", 1)
s = s.replace('background:linear-gradient(90deg,rgba(4,4,4,.92) 0%,rgba(4,4,4,.68) 38%,rgba(4,4,4,.15) 78%),linear-gradient(0deg,rgba(4,4,4,.96) 0%,rgba(4,4,4,.32) 42%,rgba(4,4,4,.12) 72%)', 'background:linear-gradient(90deg,rgba(4,4,4,.88) 0%,rgba(4,4,4,.62) 44%,rgba(4,4,4,.22) 84%),linear-gradient(0deg,rgba(4,4,4,.94) 0%,rgba(4,4,4,.36) 48%,rgba(4,4,4,.18) 78%)', 1)
s = s.replace('text-shadow:0 4px 20px rgba(0,0,0,.55)', 'color:#fffaf2;text-shadow:0 3px 12px rgba(0,0,0,.95),0 8px 30px rgba(0,0,0,.82)', 1)
s = s.replace('color:#eee7dc;font-size:17px', 'color:#fffaf2;font-size:17px', 1)
s = s.replace('text-shadow:0 2px 10px rgba(0,0,0,.7)', 'text-shadow:0 2px 12px rgba(0,0,0,.95)', 1)
s = s.replace('.eyebrow{font-size:11px;letter-spacing:.2em;color:var(--gold2);font-weight:800;margin-bottom:18px}', '.eyebrow{font-size:11px;letter-spacing:.2em;color:#ffd984;font-weight:900;margin-bottom:18px;text-shadow:0 2px 10px rgba(0,0,0,.95)}', 1)
p.write_text(s)
print('BWD hero patched: DJ-first image + stronger text contrast')
PY

node --check "$HERE/src/index.js"
python3 -m json.tool "$HERE/wrangler.jsonc" >/dev/null
grep -q 'BOOK YOUR DATE' "$PUBLIC/index.html"
grep -q 'No app. No account.' "$PUBLIC/index.html"
grep -q '/booking/' "$PUBLIC/index.html"
grep -q 'photo-1618107095181-e3ba0f53ee59' "$PUBLIC/index.html"

echo "BWD Cloudflare web build: PASS"
echo "Static source: backend/bwd-cloud-v2/public"
echo "Deploy config: cloudflare/bwd-booking/wrangler.jsonc"
