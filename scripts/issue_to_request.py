#!/usr/bin/env python3
import json, os, re
from pathlib import Path
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
body = os.environ.get('ISSUE_BODY', '')

def grab(name, default=''):
    m = re.search(rf'^{re.escape(name)}=(.*)$', body, re.M)
    return m.group(1).strip() if m else default

request_id = grab('ACC_REQUEST_ID')
project_id = grab('ACC_PROJECT_ID')
project_name = grab('ACC_PROJECT_NAME', 'ACC App')
mode = grab('ACC_MODE', 'generate')

m = re.search(r'^PROMPT:\s*\n([\s\S]*)$', body, re.M)
prompt = m.group(1).strip() if m else ''

if not request_id or not project_id or not prompt:
    raise SystemExit('Invalid ACC Builder issue payload')

req = {
    'request_id': request_id,
    'project_id': project_id,
    'project_name': project_name,
    'mode': mode,
    'prompt': prompt,
    'created_at': datetime.now(timezone.utc).isoformat(),
}

path = ROOT / 'requests' / f'{request_id}.json'
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps(req, indent=2, ensure_ascii=False), encoding='utf-8')

out = os.getenv('GITHUB_OUTPUT')
if out:
    with open(out, 'a', encoding='utf-8') as f:
        f.write(f'request_file=requests/{request_id}.json\n')
        f.write(f'request_id={request_id}\n')
        f.write(f'project_id={project_id}\n')

print(path)
