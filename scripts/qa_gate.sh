#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:18080}"
TEMPLATE_SYNC_SOURCE_BASE_URL="${TEMPLATE_SYNC_SOURCE_BASE_URL:-http://121.41.236.44:8080}"
QA_ARTIFACT_DIR="${QA_ARTIFACT_DIR:-build/qa-gate}"
QA_MODE="gate"
PYTHON_BIN="${PYTHON_BIN:-python3}"

if [[ -x ".venv/bin/python" ]]; then
  PYTHON_BIN=".venv/bin/python"
fi

mkdir -p "$QA_ARTIFACT_DIR"

echo "# Daily Gate" > "$QA_ARTIFACT_DIR/report.md"
echo "" >> "$QA_ARTIFACT_DIR/report.md"
echo "- Target: \`$BASE_URL\`" >> "$QA_ARTIFACT_DIR/report.md"
echo "- Template source: \`$TEMPLATE_SYNC_SOURCE_BASE_URL\`" >> "$QA_ARTIFACT_DIR/report.md"
echo "" >> "$QA_ARTIFACT_DIR/report.md"

echo "[qa-gate] checking application health at $BASE_URL"
curl -fsS "$BASE_URL/" >/dev/null

echo "[qa-gate] syncing template configuration"
"$PYTHON_BIN" scripts/qa_common.py sync \
  --source "$TEMPLATE_SYNC_SOURCE_BASE_URL" \
  --target "$BASE_URL" \
  --artifact-dir "$QA_ARTIFACT_DIR"

echo "[qa-gate] validating template configuration"
"$PYTHON_BIN" scripts/qa_common.py validate \
  --target "$BASE_URL" \
  --artifact-dir "$QA_ARTIFACT_DIR" \
  --mode "$QA_MODE"

if [[ "${QA_SKIP_E2E:-0}" != "1" ]]; then
  echo "[qa-gate] running student happy-path e2e matrix"
  QA_MODE="$QA_MODE" \
  QA_ARTIFACT_DIR="$QA_ARTIFACT_DIR" \
  ASSESSMENT_MATRIX="${ASSESSMENT_MATRIX:-Starters,Movers,KET,PET}" \
  BASE_URL="$BASE_URL" \
  "$PYTHON_BIN" e2e/c_end/test_student_flow.py
fi

echo "[qa-gate] report written to $QA_ARTIFACT_DIR/report.md"
