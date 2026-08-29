#!/usr/bin/env python3
"""KAI 3D Bridge v1.0

Small self-hosted queue/proxy in front of the official Hunyuan3D 2.1 API server.
It keeps long GPU generation requests off the phone connection and exposes short
polling + download endpoints for the KAI 3D FREE Android client.
"""
from __future__ import annotations

import os
import threading
import uuid
from pathlib import Path
from typing import Any

import requests
import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

HUNYUAN_URL = os.getenv("KAI3D_HUNYUAN_URL", "http://127.0.0.1:8081").rstrip("/")
HOST = os.getenv("KAI3D_HOST", "0.0.0.0")
PORT = int(os.getenv("KAI3D_PORT", "8090"))
OUT_DIR = Path(os.getenv("KAI3D_OUTPUT_DIR", str(Path.home() / "KAI3D" / "outputs")))
OUT_DIR.mkdir(parents=True, exist_ok=True)

app = FastAPI(title="KAI 3D Bridge", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

class JobRequest(BaseModel):
    image: str = Field(..., description="Base64/data URL image")
    remove_background: bool = True
    texture: bool = False
    face_count: int = Field(10000, ge=1000, le=100000)
    type: str = "glb"

_jobs: dict[str, dict[str, Any]] = {}
_lock = threading.Lock()


def _set(job_id: str, **values: Any) -> None:
    with _lock:
        _jobs.setdefault(job_id, {}).update(values)


def _run(job_id: str, payload: dict[str, Any]) -> None:
    _set(job_id, status="generating", message="Hunyuan3D sedang membuat mesh")
    try:
        image = payload.get("image", "")
        if "," in image and image.lower().startswith("data:image"):
            payload["image"] = image.split(",", 1)[1]

        # Official API exposes synchronous /generate. Keeping it behind this local
        # worker lets the phone poll without holding one long HTTP request open.
        response = requests.post(
            f"{HUNYUAN_URL}/generate",
            json=payload,
            timeout=None,
        )
        response.raise_for_status()

        content_type = response.headers.get("content-type", "")
        if "json" in content_type.lower():
            raise RuntimeError(f"Hunyuan returned JSON instead of model: {response.text[:500]}")

        path = OUT_DIR / f"{job_id}.glb"
        path.write_bytes(response.content)
        if path.stat().st_size < 256:
            raise RuntimeError("Generated GLB is unexpectedly small")
        _set(job_id, status="completed", message="GLB siap", path=str(path), size=path.stat().st_size)
    except Exception as exc:
        _set(job_id, status="error", message=str(exc))


@app.get("/health")
def health() -> dict[str, Any]:
    engine = "Hunyuan3D-2.1"
    upstream = "offline"
    try:
        r = requests.get(f"{HUNYUAN_URL}/health", timeout=3)
        upstream = "healthy" if r.ok else f"http-{r.status_code}"
    except Exception:
        pass
    return {"status": "healthy", "engine": f"KAI 3D Bridge · {engine}", "upstream": upstream}


@app.post("/jobs")
def create_job(req: JobRequest) -> dict[str, str]:
    if req.type.lower() != "glb":
        raise HTTPException(400, "KAI 3D FREE v1 currently exports GLB only")
    job_id = uuid.uuid4().hex
    payload = req.model_dump()
    _set(job_id, status="queued", message="Menunggu worker")
    threading.Thread(target=_run, args=(job_id, payload), daemon=True).start()
    return {"id": job_id, "status": "queued"}


@app.get("/jobs/{job_id}")
def job_status(job_id: str) -> dict[str, Any]:
    with _lock:
        job = dict(_jobs.get(job_id) or {})
    if not job:
        raise HTTPException(404, "Job tidak ditemukan")
    job.pop("path", None)
    return job


@app.get("/jobs/{job_id}/download")
def download(job_id: str):
    with _lock:
        job = dict(_jobs.get(job_id) or {})
    if not job:
        raise HTTPException(404, "Job tidak ditemukan")
    if job.get("status") != "completed" or not job.get("path"):
        raise HTTPException(409, "Model belum selesai")
    path = Path(job["path"])
    if not path.exists():
        raise HTTPException(410, "File hasil sudah tidak tersedia")
    return FileResponse(path, media_type="model/gltf-binary", filename=f"kai3d-{job_id[:8]}.glb")


if __name__ == "__main__":
    print(f"KAI 3D Bridge -> {HUNYUAN_URL}")
    print(f"Output          -> {OUT_DIR}")
    print(f"Listening       -> http://{HOST}:{PORT}")
    uvicorn.run(app, host=HOST, port=PORT, log_level="info")
