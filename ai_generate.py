#!/usr/bin/env python3
import json
import os
import sys
import urllib.request
import urllib.error
from pathlib import Path
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]

def safe_id(value):
    import re
    x = re.sub(r"[^a-z0-9-_]+", "-", str(value).lower())
    x = re.sub(r"-+", "-", x).strip("-")
    return x[:60] or "app"

def html_escape(s):
    import html
    return html.escape(str(s), quote=True)

def mock_html(name, prompt):
    return f"""<!doctype html>
<html lang="id"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>{html_escape(name)}</title><style>
*{{box-sizing:border-box}}body{{margin:0;font-family:system-ui;background:#f4f6fa;color:#111827}}header{{padding:18px 20px;background:#111827;color:white;font-weight:900}}
main{{padding:20px;max-width:900px;margin:auto}}.hero{{padding:26px;border-radius:22px;background:linear-gradient(135deg,#7357ff,#141827);color:white}}.card{{margin-top:14px;background:white;padding:18px;border-radius:16px;box-shadow:0 8px 25px #0000000d}}
button{{border:0;border-radius:12px;padding:12px 16px;background:white;color:#362b78;font-weight:800}}
</style></head><body><header>{html_escape(name)}</header><main><section class="hero"><small>ACC GENERATED APP</small><h1>{html_escape(prompt[:140])}</h1>
<p>GitHub Actions build berhasil. Tambahkan AI secrets agar app dibuat dinamis oleh model AI.</p><button onclick="alert('App aktif')">Mulai</button></section>
<div class="card"><b>Dashboard</b><p>Aplikasi demo tersimpan di repository dan siap dibuild menjadi APK.</p></div></main></body></html>"""

def validate_html(code):
    low = code.lower()
    if "<html" not in low or "</html>" not in low:
        raise RuntimeError("AI output is not a complete HTML document")
    if len(code) > 2_500_000:
        raise RuntimeError("Generated HTML exceeds size limit")
    return code

def call_ai(prompt, current_code, mode):
    key = os.getenv("AI_API_KEY", "").strip()
    base = os.getenv("AI_BASE_URL", "").strip().rstrip("/")
    model = os.getenv("AI_MODEL", "").strip()
    if not (key and base and model):
        return None

    system = """You are the coding engine for ACC AI Builder.
Return ONLY one complete HTML document. No markdown fences and no explanation.
All CSS and JavaScript must be inline. Do not use external CDNs, fonts, scripts, or stylesheets.
Build a polished mobile-first application that works inside an Android WebView.
Use touch-friendly controls, responsive layout, viewport-fit=cover, and localStorage for local persistence where useful.
If modifying an existing app, preserve all working features unless the requested change explicitly replaces them.
The user interface language should follow the user's prompt."""
    if mode == "modify" and current_code:
        user = f"CHANGE REQUEST:\n{prompt}\n\nCURRENT APP:\n{current_code}"
    else:
        user = f"BUILD THIS APP:\n{prompt}"

    payload = json.dumps({
        "model": model,
        "temperature": 0.25,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
    }).encode()

    req = urllib.request.Request(
        base + "/chat/completions",
        data=payload,
        headers={
            "Authorization": "Bearer " + key,
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            data = json.load(resp)
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace")
        raise RuntimeError(f"AI provider HTTP {e.code}: {body[:800]}")

    content = data.get("choices", [{}])[0].get("message", {}).get("content", "").strip()
    if content.startswith("```"):
        lines = content.splitlines()
        if lines:
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        content = "\n".join(lines)
    return validate_html(content)

def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: ai_generate.py <request.json>")
    request_path = ROOT / sys.argv[1]
    req = json.loads(request_path.read_text(encoding="utf-8"))
    project_id = safe_id(req["project_id"])
    name = str(req.get("project_name") or "ACC App")[:50]
    prompt = str(req["prompt"])
    mode = str(req.get("mode") or "generate")
    request_id = str(req["request_id"])

    app_dir = ROOT / "apps" / project_id
    app_dir.mkdir(parents=True, exist_ok=True)
    current_path = app_dir / "index.html"
    current_code = current_path.read_text(encoding="utf-8") if current_path.exists() else ""

    code = call_ai(prompt, current_code, mode)
    provider = "ai"
    if not code:
        code = mock_html(name, prompt)
        provider = "mock"

    current_path.write_text(validate_html(code), encoding="utf-8")

    old = {}
    meta_path = app_dir / "project.json"
    if meta_path.exists():
        try:
            old = json.loads(meta_path.read_text(encoding="utf-8"))
        except Exception:
            old = {}

    version = int(old.get("version", 0)) + 1
    meta = {
        "id": project_id,
        "name": name,
        "version": version,
        "provider": provider,
        "last_request_id": request_id,
        "last_prompt": prompt,
        "last_build": "building",
        "created_at": old.get("created_at") or datetime.now(timezone.utc).isoformat(),
        "updated_at": datetime.now(timezone.utc).isoformat(),
    }
    meta_path.write_text(json.dumps(meta, indent=2, ensure_ascii=False), encoding="utf-8")

    out = os.getenv("GITHUB_OUTPUT")
    if out:
        with open(out, "a", encoding="utf-8") as f:
            f.write(f"project_id={project_id}\n")
            f.write(f"app_name={name.replace(chr(10),' ')}\n")
            f.write(f"version={version}\n")
            f.write(f"provider={provider}\n")
            f.write(f"request_id={request_id}\n")

if __name__ == "__main__":
    main()
