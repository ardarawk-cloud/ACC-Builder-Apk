#!/usr/bin/env python3
import json, sys
from pathlib import Path
from datetime import datetime, timezone

ROOT=Path(__file__).resolve().parents[1]
project_id=sys.argv[1]
status=sys.argv[2]
p=ROOT/"apps"/project_id/"project.json"
data=json.loads(p.read_text(encoding="utf-8"))
data["last_build"]=status
data["updated_at"]=datetime.now(timezone.utc).isoformat()
p.write_text(json.dumps(data,indent=2,ensure_ascii=False),encoding="utf-8")
