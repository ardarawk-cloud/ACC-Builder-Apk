#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_CONFIG="$HERE/wrangler.jsonc"
RESOLVED_CONFIG="$HERE/.wrangler.resolved.jsonc"
DB_NAME="bwd_booking"

cleanup() {
  rm -f "$RESOLVED_CONFIG" /tmp/bwd-d1-list.json
}
trap cleanup EXIT

# Resolve the D1 UUID from Cloudflare at deploy time so the dashboard UI
# does not need to expose/copy the database ID manually.
npx wrangler@latest d1 list --json > /tmp/bwd-d1-list.json

BWD_BASE_CONFIG="$BASE_CONFIG" \
BWD_RESOLVED_CONFIG="$RESOLVED_CONFIG" \
BWD_D1_LIST="/tmp/bwd-d1-list.json" \
BWD_DB_NAME="$DB_NAME" \
python3 - <<'PY'
import json, os
from pathlib import Path

rows = json.loads(Path(os.environ['BWD_D1_LIST']).read_text())
if isinstance(rows, dict):
    rows = rows.get('result') or rows.get('databases') or []
if not isinstance(rows, list):
    raise SystemExit('Unexpected `wrangler d1 list --json` response')

name = os.environ['BWD_DB_NAME']
match = None
for row in rows:
    if not isinstance(row, dict):
        continue
    if row.get('name') == name or row.get('database_name') == name:
        match = row
        break
if not match:
    available = ', '.join(str(r.get('name') or r.get('database_name') or '?') for r in rows if isinstance(r, dict))
    raise SystemExit(f'D1 database {name!r} not found. Available: {available}')

db_id = match.get('uuid') or match.get('id') or match.get('database_id')
if not db_id:
    raise SystemExit(f'D1 database {name!r} found but UUID/ID missing')

base = json.loads(Path(os.environ['BWD_BASE_CONFIG']).read_text())
bindings = base.get('d1_databases') or []
if len(bindings) != 1 or bindings[0].get('binding') != 'BWD_DB':
    raise SystemExit('Expected exactly one BWD_DB D1 binding')
bindings[0]['database_name'] = name
bindings[0]['database_id'] = db_id
base['d1_databases'] = bindings
Path(os.environ['BWD_RESOLVED_CONFIG']).write_text(json.dumps(base, indent=2) + '\n')
print(f'Resolved D1 database {name} for BWD_DB binding')
PY

npx wrangler@latest deploy --config "$RESOLVED_CONFIG" --keep-vars
