#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$HERE/../.." && pwd)"
PUBLIC="$HERE/public"

rm -rf "$PUBLIC"
mkdir -p "$PUBLIC"
cp -a "$REPO_ROOT/backend/bwd-cloud-v2/public/." "$PUBLIC/"

# Reconstruct Arda's original wedding-DJ photo from text-safe repo chunks.
cat \
  "$HERE/assets/hero-wedding-dj.part00.b64" \
  "$HERE/assets/hero-wedding-dj.part01.b64" \
  "$HERE/assets/hero-wedding-dj.part02.b64" \
  "$HERE/assets/hero-wedding-dj.part03.b64" \
  "$HERE/assets/hero-wedding-dj.part04.b64" \
  "$HERE/assets/hero-wedding-dj.part05.b64" \
  "$HERE/assets/hero-wedding-dj.part06.b64" \
  "$HERE/assets/hero-wedding-dj.part07.b64" \
  | base64 --decode > "$PUBLIC/hero-wedding-dj.jpg"
test -s "$PUBLIC/hero-wedding-dj.jpg"
echo "03790f1bd449d1eb17a210c9b31482c031c3ee4b190b123066e51602b021ed73  $PUBLIC/hero-wedding-dj.jpg" | sha256sum -c -

BWD_PUBLIC="$PUBLIC" python3 - <<'PY'
from pathlib import Path
import os

p = Path(os.environ['BWD_PUBLIC']) / 'index.html'
s = p.read_text()

old = "https://images.unsplash.com/photo-1693576588167-2e7148490dc5?auto=format&fit=crop&w=1800&q=88"
new = "/hero-wedding-dj.jpg"
if old not in s:
    raise SystemExit('hero image anchor not found')
s = s.replace(old, new, 1)

s = s.replace(
    "background-position:58% center;transform:scale(1.015)",
    "background-position:center center;transform:none;filter:saturate(.96) contrast(1.04)",
    1,
)
s = s.replace(
    'background:linear-gradient(90deg,rgba(4,4,4,.92) 0%,rgba(4,4,4,.68) 38%,rgba(4,4,4,.15) 78%),linear-gradient(0deg,rgba(4,4,4,.96) 0%,rgba(4,4,4,.32) 42%,rgba(4,4,4,.12) 72%)',
    'background:linear-gradient(90deg,rgba(4,4,4,.84) 0%,rgba(4,4,4,.58) 44%,rgba(4,4,4,.18) 84%),linear-gradient(0deg,rgba(4,4,4,.90) 0%,rgba(4,4,4,.30) 48%,rgba(4,4,4,.12) 78%)',
    1,
)
s = s.replace(
    'text-shadow:0 4px 20px rgba(0,0,0,.55)',
    'color:#fffaf2;text-shadow:0 3px 12px rgba(0,0,0,.95),0 8px 30px rgba(0,0,0,.82)',
    1,
)
s = s.replace('color:#eee7dc;font-size:17px', 'color:#fffaf2;font-size:17px', 1)
s = s.replace(
    'text-shadow:0 2px 10px rgba(0,0,0,.7)',
    'text-shadow:0 2px 12px rgba(0,0,0,.95)',
    1,
)
s = s.replace(
    '.eyebrow{font-size:11px;letter-spacing:.2em;color:var(--gold2);font-weight:800;margin-bottom:18px}',
    '.eyebrow{font-size:11px;letter-spacing:.2em;color:#ffd984;font-weight:900;margin-bottom:18px;text-shadow:0 2px 10px rgba(0,0,0,.95)}',
    1,
)

contact = '''
    <div class="contact-band">
      <h3>No app. No account. Just your wedding.</h3>
      <p>Send the details from this website. You will receive a private booking link, while the Bali Wedding DJ owner dashboard receives the booking request.</p>
      <button class="btn primary" data-go="book">START BOOKING →</button>
    </div>'''
if contact not in s:
    raise SystemExit('home contact-band anchor not found')
s = s.replace(contact, '', 1)

p.write_text(s)
print('BWD web patched: user-owned DJ photo + stronger hero text + redundant home card removed')
PY

node --check "$HERE/src/index.js"
python3 -m json.tool "$HERE/wrangler.jsonc" >/dev/null
grep -q 'BOOK YOUR DATE' "$PUBLIC/index.html"
grep -q '/booking/' "$PUBLIC/index.html"
grep -q '/hero-wedding-dj.jpg' "$PUBLIC/index.html"
! grep -q 'No app. No account. Just your wedding.' "$PUBLIC/index.html"
! grep -q 'photo-1618107095181-e3ba0f53ee59' "$PUBLIC/index.html"
test -s "$PUBLIC/hero-wedding-dj.jpg"

echo "BWD Cloudflare web build: PASS"
echo "Hero asset: user-owned wedding DJ photo"
echo "Static source: backend/bwd-cloud-v2/public"
echo "Deploy config: cloudflare/bwd-booking/wrangler.jsonc"
