#!/usr/bin/env python3
import json
import os
import re
import sys
import urllib.request
import urllib.error
from pathlib import Path
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]
PRIMARY_FREE_MODEL = "qwen/qwen3-coder:free"
FALLBACK_FREE_MODEL = "openrouter/free"


def safe_id(value):
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
</style></head><body><header>{html_escape(name)}</header><main><section class="hero"><small>ACC BUILDER SAFE MODE</small><h1>AI sementara tidak tersedia</h1>
<p>{html_escape(prompt[:220])}</p></section><div class="card"><b>Build dihentikan dari mode produksi.</b><p>Jalankan ulang saat AI tersedia. ACC Builder tidak membuat tombol atau fitur palsu.</p></div></main></body></html>"""


def strip_fences(content):
    content = (content or "").strip()
    if content.startswith("```"):
        lines = content.splitlines()
        lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        content = "\n".join(lines)
    return content.strip()


def validate_html(code):
    low = code.lower()
    if "<html" not in low or "</html>" not in low:
        raise RuntimeError("AI output is not a complete HTML document")
    if "<body" not in low or "</body>" not in low:
        raise RuntimeError("AI output has no complete body")
    if len(code) < 1200:
        raise RuntimeError("Generated app is suspiciously small")
    if len(code) > 2_500_000:
        raise RuntimeError("Generated HTML exceeds size limit")
    return code


def try_validate_html(code):
    """Return validated HTML or None. Review passes are advisory and must never destroy a valid candidate."""
    if not code:
        return None
    try:
        return validate_html(code)
    except Exception:
        return None


def functional_qc(code, prompt):
    """Conservative static checks. Return a list of reasons that justify repair/failure."""
    low = code.lower()
    prompt_low = prompt.lower()
    issues = []

    buttons = len(re.findall(r"<button\b", low))
    links = len(re.findall(r"<a\b", low))
    selects = len(re.findall(r"<select\b", low))
    forms = len(re.findall(r"<form\b", low))
    scripts = len(re.findall(r"<script\b", low))

    if buttons + selects + forms >= 2 and scripts == 0:
        issues.append("interactive controls exist but there is no JavaScript")

    dead_links = len(re.findall(r'href=["\']#["\']', low))
    if dead_links >= 2:
        issues.append(f"found {dead_links} dead href=# links")

    fake_markers = [
        "coming soon", "lorem ipsum", "todo:", "javascript:void(0)",
        "fitur segera hadir", "fitur akan hadir", "placeholder content"
    ]
    found_fake = [x for x in fake_markers if x in low]
    if found_fake:
        issues.append("placeholder/fake feature markers found: " + ", ".join(found_fake))

    nav_words = ["dashboard", "content", "create", "calendar", "channels", "settings", "riwayat", "laporan"]
    nav_hits = sum(1 for w in nav_words if w in low)
    if nav_hits >= 3 and scripts == 0:
        issues.append("multi-section navigation is visible but has no switching logic")

    data_words = ["offline", "tersimpan", "simpan", "transaksi", "catatan", "stok", "kasir", "riwayat", "saldo"]
    if any(w in prompt_low for w in data_words):
        if "localstorage" not in low and "indexeddb" not in low:
            issues.append("prompt requires persistent/offline data but no localStorage/IndexedDB is implemented")

    crud_words = ["tambah", "edit", "hapus", "delete", "riwayat", "transaksi"]
    if sum(1 for w in crud_words if w in prompt_low) >= 2:
        if scripts == 0 or buttons == 0:
            issues.append("CRUD-like request lacks executable interaction logic")

    social_words = ["social media", "poster", "caption", "publish", "channel", "calendar", "content"]
    if sum(1 for w in social_words if w in prompt_low) >= 2:
        text_len = len(re.sub(r"<[^>]+>", " ", code))
        if text_len < 900:
            issues.append("complex social-media app is too empty for requested scope")

    return issues


def request_chat(base, key, model, messages, temperature=0.18, timeout=240):
    payload = json.dumps({
        "model": model,
        "temperature": temperature,
        "messages": messages,
    }).encode()
    req = urllib.request.Request(
        base.rstrip("/") + "/chat/completions",
        data=payload,
        headers={
            "Authorization": "Bearer " + key,
            "Content-Type": "application/json",
            "HTTP-Referer": "https://ardarawk-cloud.github.io/ACC-Builder-Apk/",
            "X-Title": "ACC AI Builder",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            data = json.load(resp)
    except urllib.error.HTTPError as e:
        body = e.read().decode(errors="replace")
        raise RuntimeError(f"AI provider HTTP {e.code} on {model}: {body[:800]}")
    except Exception as e:
        raise RuntimeError(f"AI request failed on {model}: {e}")

    content = data.get("choices", [{}])[0].get("message", {}).get("content", "")
    return strip_fences(content), data.get("model") or model


def model_candidates():
    configured = os.getenv("AI_MODEL", "").strip()
    candidates = []
    # Prefer the explicitly configured model. Free models are fallbacks, not the quality ceiling.
    if configured:
        candidates.append(configured)
    if PRIMARY_FREE_MODEL not in candidates:
        candidates.append(PRIMARY_FREE_MODEL)
    if FALLBACK_FREE_MODEL not in candidates:
        candidates.append(FALLBACK_FREE_MODEL)
    return candidates


def call_best_model(messages, temperature=0.18):
    key = os.getenv("AI_API_KEY", "").strip()
    base = os.getenv("AI_BASE_URL", "").strip().rstrip("/")
    if not (key and base):
        return None, None

    errors = []
    for model in model_candidates():
        try:
            content, actual = request_chat(base, key, model, messages, temperature=temperature)
            if content:
                return content, actual
        except Exception as e:
            errors.append(str(e))
    raise RuntimeError("All AI candidates failed: " + " | ".join(errors))


SYSTEM_PROMPT = """You are the principal software engineer and QA gate for ACC AI Builder.
Your job is to produce a REAL, WORKING, mobile-first Android WebView application as ONE complete HTML document.

NON-NEGOTIABLE RULES:
1. Return ONLY the complete HTML document. No markdown fences, explanations, or commentary.
2. All CSS and JavaScript must be inline. No CDN, external fonts, frameworks, remote scripts, or external stylesheets.
3. Every visible button, tab, menu, form, search field, filter, toggle, card action, and navigation item MUST work.
4. NEVER create decorative/fake controls. NEVER create empty tabs or placeholder pages. If a requested feature cannot be truly implemented offline, do not fake it: show a clearly labelled unavailable state with an explanation.
5. For data-oriented apps, implement actual local persistence using localStorage or IndexedDB. Data must survive app close/reopen.
6. Implement real empty states, validation, error handling, confirmations for destructive actions, and responsive mobile layout.
7. For CRUD features, implement create/read/update/delete completely where requested.
8. For multi-tab apps, every tab must switch to meaningful working content. The first screen must never be an empty shell.
9. Use semantic HTML, accessible labels, touch targets, and viewport-fit=cover.
10. Do not claim integrations such as Facebook/Instagram publishing unless the app actually has authenticated API access. Such integrations must be marked as requiring connection/setup rather than simulated.
11. The app language follows the user's prompt. If Indonesian is used, keep the UI Indonesian.
12. Before returning code, internally audit every requested feature and every visible control. Fix defects before final output.
"""


def build_initial(prompt, current_code, mode):
    if mode == "modify" and current_code:
        user = f"""CHANGE REQUEST:\n{prompt}\n\nCURRENT APP SOURCE:\n{current_code}\n\nApply the change without breaking existing working features. Return the entire corrected HTML."""
    else:
        user = f"""BUILD THIS APPLICATION:\n{prompt}\n\nBuild a complete usable v1, not a mockup or wireframe. Return the entire HTML."""
    return call_best_model([
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": user},
    ], temperature=0.16)


def repair_app(prompt, code, issues, pass_no):
    issue_text = "\n".join(f"- {x}" for x in issues) if issues else "- Perform a strict functional audit against the request and fix anything incomplete."
    repair = f"""The following generated app must pass a production gate before an APK can be built.

ORIGINAL USER REQUEST:\n{prompt}\n
QC FINDINGS:\n{issue_text}\n
CURRENT HTML:\n{code}\n
Repair the application completely. Verify every navigation item and button has real behavior, requested data persists, no requested page is empty, and no fake feature exists. Return ONLY the full repaired HTML document. This is repair pass {pass_no}."""
    return call_best_model([
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": repair},
    ], temperature=0.10)


def generate_with_qc(prompt, current_code, mode):
    code, model = build_initial(prompt, current_code, mode)
    if not code:
        return None, None, ["AI unavailable"]
    code = validate_html(code)

    # Review passes may improve a valid candidate, but an incomplete reviewer response must never destroy it.
    first_issues = functional_qc(code, prompt)
    reviewed, review_model = repair_app(prompt, code, first_issues, 1)
    reviewed_valid = try_validate_html(reviewed)
    if reviewed_valid:
        code = reviewed_valid
        model = review_model or model

    remaining = functional_qc(code, prompt)
    if remaining:
        repaired, repair_model = repair_app(prompt, code, remaining, 2)
        repaired_valid = try_validate_html(repaired)
        if repaired_valid:
            code = repaired_valid
            model = repair_model or model
        remaining = functional_qc(code, prompt)

    if remaining:
        raise RuntimeError("FUNCTIONAL_QC_FAILED: " + " | ".join(remaining))
    return code, model, []


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

    code, actual_model, qc_issues = generate_with_qc(prompt, current_code, mode)
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
        "model": actual_model or "none",
        "qc": "passed" if provider == "ai" and not qc_issues else "safe-mode",
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
            f.write(f"model={(actual_model or 'none').replace(chr(10),' ')}\n")
            f.write(f"request_id={request_id}\n")


if __name__ == "__main__":
    main()
