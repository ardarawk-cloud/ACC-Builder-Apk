#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

# KIN Termux alpha launcher.
# Runs from the Termux host shell and keeps mutable alpha data outside the git checkout.

TERMUX_HOME="${HOME}"
SOURCE_DIR="${KIN_TERMUX_SOURCE_DIR:-${TERMUX_HOME}/kin-source}"
DATA_DIR="${KIN_TERMUX_DATA_DIR:-${TERMUX_HOME}/kin-data}"
BACKEND_DIR="${SOURCE_DIR}/kin/backend"
LEGACY_DB="${BACKEND_DIR}/kin.db"
PERSISTENT_DB="${DATA_DIR}/kin.db"
PORT="${KIN_TERMUX_PORT:-8020}"
SESSION="${KIN_TERMUX_SESSION:-kin}"

if [[ ! -d "${BACKEND_DIR}" ]]; then
  echo "KIN backend not found: ${BACKEND_DIR}" >&2
  exit 1
fi

mkdir -p "${DATA_DIR}"

# One-time, non-destructive migration from the old repo-relative SQLite database.
# Never overwrite an existing persistent database.
if [[ ! -f "${PERSISTENT_DB}" && -f "${LEGACY_DB}" ]]; then
  cp -p "${LEGACY_DB}" "${PERSISTENT_DB}"
  echo "Migrated legacy KIN DB -> ${PERSISTENT_DB}"
elif [[ -f "${PERSISTENT_DB}" ]]; then
  echo "Using persistent KIN DB: ${PERSISTENT_DB}"
else
  echo "No previous KIN DB found; a new persistent DB will be created at ${PERSISTENT_DB}"
fi

# Keep the legacy file untouched as a recovery copy if it exists.
# Mutable runtime data lives only under DATA_DIR from this point forward.

tmux kill-session -t "${SESSION}" 2>/dev/null || true

tmux new-session -d -s "${SESSION}" \
  "proot-distro login ubuntu \
  --bind=${SOURCE_DIR}:/root/kin-source \
  --bind=${DATA_DIR}:/root/kin-data \
  -- bash -lc 'cd /root/kin-source/kin/backend && export KIN_DATABASE_URL=sqlite:////root/kin-data/kin.db && exec python -m uvicorn app.main:app --host 127.0.0.1 --port ${PORT} --proxy-headers --forwarded-allow-ips=\"*\" --no-server-header'"

sleep 4

echo "KIN session: ${SESSION}"
echo "KIN port: ${PORT}"
echo "KIN DB: ${PERSISTENT_DB}"
curl -fsS "http://127.0.0.1:${PORT}/health"
echo
