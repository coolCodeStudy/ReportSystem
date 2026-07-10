#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:18080}"
TEMPLATE_SYNC_SOURCE_BASE_URL="${TEMPLATE_SYNC_SOURCE_BASE_URL:-http://121.41.236.44:8080}"
QA_ARTIFACT_DIR="${QA_ARTIFACT_DIR:-build/bug-hunt}"
QA_MODE="hunt"
QA_DOCX_DEEP="${QA_DOCX_DEEP:-1}"
QA_VISUAL_QA="${QA_VISUAL_QA:-1}"
PYTHON_BIN="${PYTHON_BIN:-python3}"

if [[ -x ".venv/bin/python" ]]; then
  PYTHON_BIN=".venv/bin/python"
fi

mkdir -p "$QA_ARTIFACT_DIR"

echo "# Monthly Bug Hunt" > "$QA_ARTIFACT_DIR/report.md"
echo "" >> "$QA_ARTIFACT_DIR/report.md"
echo "- Target: \`$BASE_URL\`" >> "$QA_ARTIFACT_DIR/report.md"
echo "- Template source: \`$TEMPLATE_SYNC_SOURCE_BASE_URL\`" >> "$QA_ARTIFACT_DIR/report.md"
echo "" >> "$QA_ARTIFACT_DIR/report.md"

echo "[bug-hunt] running QA harness contract tests"
"$PYTHON_BIN" -m unittest \
  scripts/test_qa_common.py \
  scripts/test_e2e_student_flow_contract.py

echo "[bug-hunt] checking application health at $BASE_URL"
curl -fsS "$BASE_URL/" >/dev/null

echo "[bug-hunt] syncing template configuration"
"$PYTHON_BIN" scripts/qa_common.py sync \
  --source "$TEMPLATE_SYNC_SOURCE_BASE_URL" \
  --target "$BASE_URL" \
  --artifact-dir "$QA_ARTIFACT_DIR"

echo "[bug-hunt] validating template configuration"
"$PYTHON_BIN" scripts/qa_common.py validate \
  --target "$BASE_URL" \
  --artifact-dir "$QA_ARTIFACT_DIR" \
  --mode "$QA_MODE"

if [[ "${QA_SKIP_ADMIN_E2E:-0}" != "1" ]]; then
  echo "[bug-hunt] running admin configuration patrol"
  BASE_URL="$BASE_URL" "$PYTHON_BIN" e2e/b_end/test_admin_flow.py
fi

if [[ "${QA_SKIP_E2E:-0}" != "1" ]]; then
  echo "[bug-hunt] running broad student e2e matrix"
  QA_MODE="$QA_MODE" \
  QA_DOCX_DEEP="$QA_DOCX_DEEP" \
  QA_VISUAL_QA="$QA_VISUAL_QA" \
  QA_ARTIFACT_DIR="$QA_ARTIFACT_DIR" \
  ASSESSMENT_MATRIX="${ASSESSMENT_MATRIX:-Starters,Movers,Flyers,KET,PET,IELTS,TOEFL Junior,MAP}" \
  BASE_URL="$BASE_URL" \
  "$PYTHON_BIN" e2e/c_end/test_student_flow.py
fi

echo "[bug-hunt] report written to $QA_ARTIFACT_DIR/report.md"
