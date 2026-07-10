#!/usr/bin/env python3
"""Shared QA helpers for template sync, config validation, and DOCX smoke checks."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path
from typing import Any


DAILY_TYPES = ["Starters", "Movers", "Flyers", "KET", "PET"]
HUNT_TYPES = DAILY_TYPES + ["IELTS", "TOEFL Junior", "MAP"]

STATIC_TEMPLATE_KEYS = [
    "GLOBAL_ASSESSMENT_DESCRIPTIONS",
    "GLOBAL_CAPABILITY_MATRIX_CSV",
    "GLOBAL_BASIC_COLUMNS",
    "GLOBAL_COURSE_PLAN_DEFAULT",
    "GLOBAL_COURSE_PLAN_NOTE_DEFAULT",
    "GLOBAL_TEACHING_APPROACH_TEMPLATE",
    "GLOBAL_TEACHING_CHECKLIST_TEMPLATE",
    "GLOBAL_COURSE_FREQUENCY_TEMPLATE",
    "GLOBAL_PLAN_RISK_TEMPLATE",
    "GLOBAL_ANALYSIS_CONFIG_READING",
    "GLOBAL_CAUSE_ANALYSIS_READING",
    "GLOBAL_ANALYSIS_CONFIG_LISTENING",
    "GLOBAL_CAUSE_ANALYSIS_LISTENING",
    "GLOBAL_ANALYSIS_CONFIG_SPEAKING",
    "GLOBAL_CAUSE_ANALYSIS_SPEAKING",
    "GLOBAL_ANALYSIS_CONFIG_WRITING",
    "GLOBAL_CAUSE_ANALYSIS_WRITING",
    "GLOBAL_ANALYSIS_CONFIG_LANGUAGE_USE",
    "GLOBAL_CAUSE_ANALYSIS_LANGUAGE_USE",
    "GLOBAL_ANALYSIS_CONFIG_LEARNING_LITERACY",
    "GLOBAL_CAUSE_ANALYSIS_LEARNING_LITERACY",
]


def normalize_key(raw: str) -> str:
    return re.sub(r"[^A-Z0-9]+", "_", raw.strip().upper()).strip("_")


def now_stamp() -> str:
    return dt.datetime.now().strftime("%Y%m%d-%H%M%S")


def ensure_artifact_dir(path: str | Path) -> Path:
    artifact_dir = Path(path)
    artifact_dir.mkdir(parents=True, exist_ok=True)
    return artifact_dir


def api_get_json(base_url: str, path: str) -> Any:
    url = base_url.rstrip("/") + path
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def api_post_json(base_url: str, path: str, payload: dict[str, Any]) -> Any:
    url = base_url.rstrip("/") + path
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        method="POST",
        headers={"Content-Type": "application/json", "Accept": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def fetch_config(base_url: str, key: str) -> str:
    encoded = urllib.parse.quote(key, safe="")
    body = api_get_json(base_url, f"/admin/api/config/{encoded}")
    data = body.get("data", body) if isinstance(body, dict) else {}
    if isinstance(data, dict):
        return str(data.get("value", ""))
    return ""


def save_config(base_url: str, key: str, value: str) -> None:
    encoded = urllib.parse.quote(key, safe="")
    body = api_post_json(base_url, f"/admin/api/config/{encoded}", {"value": value})
    if isinstance(body, dict) and body.get("code") not in (None, 200):
        raise RuntimeError(f"Save failed for {key}: {body}")


def parse_json(value: str, key: str, issues: list[dict[str, str]]) -> Any | None:
    if not value.strip():
        issues.append({"severity": "ERROR", "key": key, "message": f"{key} is empty."})
        return None
    try:
        return json.loads(value)
    except json.JSONDecodeError as exc:
        issues.append({"severity": "ERROR", "key": key, "message": f"{key} is not valid JSON: {exc}"})
        return None


def type_candidates(item: dict[str, Any] | str) -> list[str]:
    if isinstance(item, dict):
        raw = [str(item.get("id", "")), str(item.get("name", ""))]
    else:
        raw = [item]
    candidates: list[str] = []
    for value in raw:
        for candidate in (value, normalize_key(value)):
            if candidate and candidate not in candidates:
                candidates.append(candidate)
    return candidates


def subject_candidates(subject: dict[str, Any] | str) -> list[str]:
    if isinstance(subject, dict):
        raw = [str(subject.get("key", "")), str(subject.get("id", "")), str(subject.get("name", ""))]
    else:
        raw = [subject]
    candidates: list[str] = []
    for value in raw:
        for candidate in (value, normalize_key(value)):
            if candidate and candidate not in candidates:
                candidates.append(candidate)
    return candidates


def first_non_empty_config(base_url: str, keys: list[str]) -> tuple[str, str] | None:
    for key in keys:
        value = fetch_config(base_url, key)
        if value.strip():
            return key, value
    return None


def discover_template_configs(base_url: str) -> tuple[dict[str, str], list[str]]:
    configs: dict[str, str] = {}
    missing: list[str] = []

    for key in STATIC_TEMPLATE_KEYS:
        value = fetch_config(base_url, key)
        if value.strip():
            configs[key] = value
        else:
            missing.append(key)

    issues: list[dict[str, str]] = []
    descriptions = parse_json(configs.get("GLOBAL_ASSESSMENT_DESCRIPTIONS", ""), "GLOBAL_ASSESSMENT_DESCRIPTIONS", issues)
    if not isinstance(descriptions, list):
        return configs, missing

    for assessment in descriptions:
        if not isinstance(assessment, dict):
            continue
        subj_keys = [f"GLOBAL_SUBJECTS_{candidate}" for candidate in type_candidates(assessment)]
        found_subjects = first_non_empty_config(base_url, subj_keys)
        if not found_subjects:
            missing.extend(subj_keys[:1])
            continue
        subject_config_key, subject_config_value = found_subjects
        configs[subject_config_key] = subject_config_value
        subjects = parse_json(subject_config_value, subject_config_key, issues)
        if not isinstance(subjects, list):
            continue

        for subject in subjects:
            if not isinstance(subject, dict):
                continue
            for prefix in ("GLOBAL_ANALYSIS_CONFIG_", "GLOBAL_CAUSE_ANALYSIS_", "GLOBAL_SCORE_RULE_"):
                keys = [
                    f"{prefix}{type_candidate}_{subject_candidate}"
                    for type_candidate in type_candidates(assessment)
                    for subject_candidate in subject_candidates(subject)
                ]
                found = first_non_empty_config(base_url, keys)
                if found:
                    configs[found[0]] = found[1]
                else:
                    missing.append(keys[0])

    return configs, sorted(set(missing))


def sync_templates(source_base_url: str, target_base_url: str, artifact_dir: Path) -> int:
    if source_base_url.rstrip("/") == target_base_url.rstrip("/"):
        append_report(artifact_dir, "## Template Sync\n\nSkipped because source and target base URLs are identical.\n")
        return 0

    source_configs, missing_source = discover_template_configs(source_base_url)
    snapshot = {key: fetch_config(target_base_url, key) for key in sorted(source_configs)}
    snapshot_path = artifact_dir / f"system-config-snapshot-before-sync-{now_stamp()}.json"
    snapshot_path.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2), encoding="utf-8")

    for key, value in source_configs.items():
        save_config(target_base_url, key, value)

    lines = [
        "## Template Sync",
        "",
        f"- Source: `{source_base_url}`",
        f"- Target: `{target_base_url}`",
        f"- Synced keys: `{len(source_configs)}`",
        f"- Snapshot: `{snapshot_path}`",
    ]
    if missing_source:
        lines.append(f"- Missing/empty source keys: `{len(missing_source)}`")
        lines.extend(f"  - `{key}`" for key in missing_source[:30])
    append_report(artifact_dir, "\n".join(lines) + "\n\n")
    return len(source_configs)


def validate_configs(base_url: str, artifact_dir: Path, mode: str) -> int:
    configs, missing = discover_template_configs(base_url)
    issues: list[dict[str, str]] = []
    required_types = DAILY_TYPES if mode == "gate" else HUNT_TYPES

    descriptions = parse_json(configs.get("GLOBAL_ASSESSMENT_DESCRIPTIONS", ""), "GLOBAL_ASSESSMENT_DESCRIPTIONS", issues)
    available_types: set[str] = set()
    required_type_config_candidates: set[str] = set()
    if isinstance(descriptions, list):
        for item in descriptions:
            if isinstance(item, dict):
                candidates = type_candidates(item)
                available_types.update(candidate.lower() for candidate in candidates)
                item_names = {candidate.lower() for candidate in candidates}
                if any(candidate.lower() in {value.lower() for value in item_names} for expected in required_types for candidate in type_candidates(expected)):
                    required_type_config_candidates.update(candidates)

    for expected in required_types:
        if not any(candidate.lower() in available_types for candidate in type_candidates(expected)):
            issues.append({
                "severity": "ERROR",
                "key": "GLOBAL_ASSESSMENT_DESCRIPTIONS",
                "message": f"Missing assessment type required by {mode}: {expected}",
            })

    if "Pre-A1" not in configs.get("GLOBAL_CAPABILITY_MATRIX_CSV", ""):
        issues.append({
            "severity": "ERROR",
            "key": "GLOBAL_CAPABILITY_MATRIX_CSV",
            "message": "Capability matrix must include Pre-A1 for the top Lingoland row.",
        })

    for key, value in sorted(configs.items()):
        if key == "GLOBAL_CAPABILITY_MATRIX_CSV":
            continue
        if key.startswith(("GLOBAL_SUBJECTS_", "GLOBAL_ANALYSIS_CONFIG_", "GLOBAL_CAUSE_ANALYSIS_", "GLOBAL_SCORE_RULE_")):
            parse_json(value, key, issues)

    for key in missing:
        severity = missing_config_severity(key, mode, required_type_config_candidates)
        issues.append({"severity": severity, "key": key, "message": f"Missing or empty config: {key}"})

    errors = [issue for issue in issues if issue["severity"] == "ERROR"]
    warnings = [issue for issue in issues if issue["severity"] == "WARNING"]
    lines = [
        "## Config Validator",
        "",
        f"- Target: `{base_url}`",
        f"- Mode: `{mode}`",
        f"- Config keys discovered: `{len(configs)}`",
        f"- Errors: `{len(errors)}`",
        f"- Warnings: `{len(warnings)}`",
        "",
    ]
    if issues:
        lines.append("| Severity | Key | Message |")
        lines.append("| --- | --- | --- |")
        for issue in issues:
            lines.append(f"| {issue['severity']} | `{issue['key']}` | {issue['message']} |")
    else:
        lines.append("No blocking config issues found.")
    append_report(artifact_dir, "\n".join(lines) + "\n\n")
    return 1 if errors else 0


def missing_config_severity(key: str, mode: str, required_type_config_candidates: set[str]) -> str:
    dynamic_prefixes = ("GLOBAL_SUBJECTS_", "GLOBAL_ANALYSIS_CONFIG_", "GLOBAL_CAUSE_ANALYSIS_", "GLOBAL_SCORE_RULE_")
    if not key.startswith(dynamic_prefixes):
        return "WARNING"
    if mode != "gate":
        return "ERROR"
    return "ERROR" if any(candidate and candidate in key for candidate in required_type_config_candidates) else "WARNING"


def validate_docx(path: Path, artifact_dir: Path) -> int:
    issues: list[str] = []
    deep_artifacts: list[Path] = []
    visual_report = ""
    if not path.exists():
        issues.append(f"DOCX does not exist: {path}")
    elif not zipfile.is_zipfile(path):
        issues.append(f"DOCX is not a valid OOXML zip: {path}")
    else:
        with zipfile.ZipFile(path) as zf:
            names = set(zf.namelist())
            if "word/document.xml" not in names:
                issues.append("Missing word/document.xml")
            else:
                xml = zf.read("word/document.xml").decode("utf-8", errors="ignore")
                if re.search(r"\{[A-Za-z0-9_]+\}", xml):
                    issues.append("Unresolved placeholder token remains in document.xml")
                for placeholder_text in ("封面文案标题", "请输入", "TODO"):
                    if placeholder_text in xml:
                        issues.append(f"Template placeholder text remains in document.xml: {placeholder_text}")
                for phrase in ("测评", "语言教学", "费用"):
                    if phrase not in xml:
                        issues.append(f"Expected section text missing from DOCX XML: {phrase}")

        if os.getenv("QA_DOCX_DEEP", "0") == "1":
            deep_errors, deep_artifacts, visual_report = render_docx_for_deep_check(path, artifact_dir)
            issues.extend(deep_errors)

    lines = ["## DOCX Quick Check", "", f"- File: `{path}`", f"- Errors: `{len(issues)}`"]
    lines.extend(f"- Rendered artifact: `{artifact}`" for artifact in deep_artifacts)
    lines.extend(f"- {issue}" for issue in issues)
    report = "\n".join(lines) + "\n\n"
    if visual_report:
        report += visual_report + "\n\n"
    append_report(artifact_dir, report)
    return 1 if issues else 0


def render_docx_for_deep_check(path: Path, artifact_dir: Path) -> tuple[list[str], list[Path], str]:
    errors: list[str] = []
    artifacts: list[Path] = []
    visual_report = ""
    render_dir = artifact_dir / "docx-render"
    render_dir.mkdir(parents=True, exist_ok=True)

    libreoffice = shutil.which("soffice") or shutil.which("libreoffice")
    if not libreoffice:
        return ["QA_DOCX_DEEP=1 but LibreOffice/soffice is not available."], artifacts, visual_report

    try:
        with tempfile.TemporaryDirectory(prefix="reportsystem-lo-") as profile_dir:
            subprocess.run(
                [
                    libreoffice,
                    "--headless",
                    f"-env:UserInstallation={Path(profile_dir).resolve().as_uri()}",
                    "--convert-to",
                    "pdf",
                    "--outdir",
                    str(render_dir),
                    str(path),
                ],
                check=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=120,
            )
    except subprocess.CalledProcessError as exc:
        return [f"LibreOffice failed to render DOCX: {exc.stdout}"], artifacts, visual_report
    except subprocess.TimeoutExpired:
        return ["LibreOffice DOCX render timed out."], artifacts, visual_report

    pdf_path = render_dir / f"{path.stem}.pdf"
    if not pdf_path.exists():
        return [f"LibreOffice did not produce expected PDF: {pdf_path}"], artifacts, visual_report
    artifacts.append(pdf_path)

    pdftoppm = shutil.which("pdftoppm")
    if pdftoppm:
        png_prefix = render_dir / path.stem
        try:
            subprocess.run(
                [pdftoppm, "-png", str(pdf_path), str(png_prefix)],
                check=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=60,
            )
            png_paths = rendered_png_paths(render_dir, path.stem)
            artifacts.extend(png_paths)
            if os.getenv("QA_VISUAL_QA", "0") == "1":
                page_texts = extract_pdf_page_texts(pdf_path)
                visual_errors, visual_details = run_visual_qa_checks(png_paths, page_texts)
                errors.extend(visual_errors)
                visual_report = format_visual_qa_report(pdf_path, png_paths, visual_errors, visual_details)
        except subprocess.CalledProcessError as exc:
            errors.append(f"pdftoppm failed to render PDF preview: {exc.stdout}")
        except subprocess.TimeoutExpired:
            errors.append("pdftoppm PDF preview render timed out.")

    return errors, artifacts, visual_report


def rendered_png_paths(render_dir: Path, stem: str) -> list[Path]:
    candidates = list(render_dir.glob(f"{stem}-*.png"))
    if not candidates:
        single = render_dir / f"{stem}.png"
        if single.exists():
            candidates = [single]

    def sort_key(path: Path) -> tuple[int, str]:
        match = re.search(r"-(\d+)\.png$", path.name)
        return (int(match.group(1)) if match else 1, path.name)

    return sorted(candidates, key=sort_key)


def analyze_png_visual_metrics(path: Path) -> dict[str, Any]:
    from PIL import Image

    with Image.open(path) as image:
        rgb = image.convert("RGB")
        width, height = rgb.size
        if max(width, height) > 900:
            rgb.thumbnail((900, 900))
        pixels = list(rgb.getdata())

    total = max(len(pixels), 1)
    non_white = 0
    dark = 0
    for red, green, blue in pixels:
        if red < 245 or green < 245 or blue < 245:
            non_white += 1
        if red < 80 and green < 80 and blue < 80:
            dark += 1

    non_white_ratio = non_white / total
    dark_ratio = dark / total
    return {
        "path": str(path),
        "width": width,
        "height": height,
        "non_white_ratio": non_white_ratio,
        "dark_ratio": dark_ratio,
        "is_blank": non_white_ratio < 0.005,
    }


def analyze_status_icon_pixels(path: Path) -> dict[str, int]:
    from PIL import Image

    with Image.open(path) as image:
        rgb = image.convert("RGB")
        width, height = rgb.size
        content = rgb.crop((int(width * 0.30), int(height * 0.18), int(width * 0.90), int(height * 0.92)))
        if max(content.size) > 900:
            content.thumbnail((900, 900))
        pixels = list(content.getdata())

    green_pixels = sum(
        1 for red, green, blue in pixels
        if green > 120 and green > red * 1.25 and green > blue * 1.25
    )
    red_pixels = sum(
        1 for red, green, blue in pixels
        if red > 180 and green < 105 and blue < 105
    )
    return {
        "green_pixels": green_pixels,
        "red_pixels": red_pixels,
    }


def run_visual_qa_checks(png_paths: list[Path], page_texts: dict[int, str]) -> tuple[list[str], dict[str, Any]]:
    issues: list[str] = []
    review_items: list[str] = []
    metrics = []
    status_icon_metrics = []
    keyword_pages = find_keyword_pages(page_texts, ["费用", "课程价目表", "测评分析", "语言教学", "教学计划"])

    if not png_paths:
        issues.append("No rendered PNG pages were produced for Visual QA.")

    for page_number, png_path in enumerate(png_paths, start=1):
        page_metrics = analyze_png_visual_metrics(png_path)
        page_metrics["page"] = page_number
        metrics.append(page_metrics)
        if page_metrics["is_blank"]:
            issues.append(f"Page {page_number} appears blank after rendering.")

        page_text = page_texts.get(page_number, "")
        expected_positive = "✅" in page_text
        expected_negative = "❗" in page_text
        if expected_positive or expected_negative:
            icon_metrics = analyze_status_icon_pixels(png_path)
            icon_metrics.update({
                "page": page_number,
                "expected_positive": expected_positive,
                "expected_negative": expected_negative,
            })
            status_icon_metrics.append(icon_metrics)

            missing_icons = []
            if expected_positive and icon_metrics["green_pixels"] < 20:
                missing_icons.append("✅")
            if expected_negative and icon_metrics["red_pixels"] < 20:
                missing_icons.append("❗")
            if missing_icons:
                issues.append(
                    f"Page {page_number} status icons may be invisible after rendering: missing {', '.join(missing_icons)}."
                )

    fee_pages = set(keyword_pages.get("费用", []))
    price_pages = set(keyword_pages.get("课程价目表", []))
    if fee_pages and price_pages and fee_pages.isdisjoint(price_pages):
        review_items.append(
            f"费用 title and 课程价目表 appear on different pages: 费用={sorted(fee_pages)}, 课程价目表={sorted(price_pages)}"
        )
    metrics_by_page = {item["page"]: item for item in metrics}
    if fee_pages:
        for fee_page in sorted(fee_pages):
            next_page = fee_page + 1
            fee_metrics = metrics_by_page.get(fee_page)
            next_metrics = metrics_by_page.get(next_page)
            fee_page_already_has_visual = bool(fee_metrics and fee_metrics["non_white_ratio"] > 0.30)
            if not price_pages and not fee_page_already_has_visual and next_metrics and next_metrics["non_white_ratio"] > 0.75:
                review_items.append(
                    f"费用 appears on page {fee_page}, followed by dense image page {next_page}; the fee image may be split from its title."
                )

    if len(png_paths) < 3:
        issues.append(f"Rendered document has suspiciously few pages: {len(png_paths)}")

    return issues, {
        "page_count": len(png_paths),
        "keyword_pages": keyword_pages,
        "page_metrics": metrics,
        "status_icon_metrics": status_icon_metrics,
        "review_items": review_items,
    }


def find_keyword_pages(page_texts: dict[int, str], keywords: list[str]) -> dict[str, list[int]]:
    result: dict[str, list[int]] = {keyword: [] for keyword in keywords}
    for page, text in page_texts.items():
        normalized = re.sub(r"\s+", "", text)
        for keyword in keywords:
            if keyword in text or keyword in normalized:
                result[keyword].append(page)
    return result


def extract_pdf_page_texts(pdf_path: Path) -> dict[int, str]:
    page_count = get_pdf_page_count(pdf_path)
    pdftotext = shutil.which("pdftotext")
    if not pdftotext or page_count <= 0:
        return {}
    texts: dict[int, str] = {}
    for page in range(1, page_count + 1):
        try:
            result = subprocess.run(
                [pdftotext, "-layout", "-f", str(page), "-l", str(page), str(pdf_path), "-"],
                check=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=20,
            )
            texts[page] = result.stdout
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
            texts[page] = ""
    return texts


def get_pdf_page_count(pdf_path: Path) -> int:
    pdfinfo = shutil.which("pdfinfo")
    if not pdfinfo:
        return 0
    try:
        result = subprocess.run(
            [pdfinfo, str(pdf_path)],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=20,
        )
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
        return 0
    match = re.search(r"^Pages:\s+(\d+)", result.stdout, flags=re.MULTILINE)
    return int(match.group(1)) if match else 0


def format_visual_qa_report(
    pdf_path: Path,
    png_paths: list[Path],
    issues: list[str],
    details: dict[str, Any],
) -> str:
    lines = [
        "## DOCX Visual QA",
        "",
        f"- PDF: `{pdf_path}`",
        f"- Rendered pages: `{details.get('page_count', len(png_paths))}`",
        f"- Visual hard issues: `{len(issues)}`",
        f"- Visual review items: `{len(details.get('review_items', []))}`",
    ]
    for png_path in png_paths:
        lines.append(f"- Page PNG: `{png_path}`")
    keyword_pages = details.get("keyword_pages", {})
    if keyword_pages:
        lines.append("")
        lines.append("| Keyword | Pages |")
        lines.append("| --- | --- |")
        for keyword, pages in keyword_pages.items():
            lines.append(f"| {keyword} | {pages or '-'} |")
    metrics = details.get("page_metrics", [])
    if metrics:
        lines.append("")
        lines.append("| Page | Non-white ratio | Blank? |")
        lines.append("| --- | --- | --- |")
        for item in metrics:
            lines.append(f"| {item['page']} | {item['non_white_ratio']:.4f} | {item['is_blank']} |")
    status_metrics = details.get("status_icon_metrics", [])
    if status_metrics:
        lines.append("")
        lines.append("| Status page | Green pixels | Red pixels |")
        lines.append("| --- | --- | --- |")
        for item in status_metrics:
            lines.append(f"| {item['page']} | {item['green_pixels']} | {item['red_pixels']} |")
    if issues:
        lines.append("")
        lines.append("Hard issues:")
        lines.extend(f"- {issue}" for issue in issues)
    review_items = details.get("review_items", [])
    if review_items:
        lines.append("")
        lines.append("Review items:")
        lines.extend(f"- {item}" for item in review_items)
    return "\n".join(lines)


def append_report(artifact_dir: Path, markdown: str) -> None:
    report = artifact_dir / "report.md"
    if not report.exists():
        report.write_text(f"# ReportSystem QA Report\n\nGenerated: {dt.datetime.now().isoformat()}\n\n", encoding="utf-8")
    with report.open("a", encoding="utf-8") as fh:
        fh.write(markdown)


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)

    sync = sub.add_parser("sync")
    sync.add_argument("--source", required=True)
    sync.add_argument("--target", required=True)
    sync.add_argument("--artifact-dir", required=True)

    validate = sub.add_parser("validate")
    validate.add_argument("--target", required=True)
    validate.add_argument("--artifact-dir", required=True)
    validate.add_argument("--mode", choices=["gate", "hunt"], default="gate")

    docx = sub.add_parser("docx")
    docx.add_argument("--path", required=True)
    docx.add_argument("--artifact-dir", required=True)

    args = parser.parse_args()
    artifact_dir = ensure_artifact_dir(args.artifact_dir)
    try:
        if args.command == "sync":
            sync_templates(args.source, args.target, artifact_dir)
            return 0
        if args.command == "validate":
            return validate_configs(args.target, artifact_dir, args.mode)
        if args.command == "docx":
            return validate_docx(Path(args.path), artifact_dir)
    except (urllib.error.URLError, TimeoutError, RuntimeError) as exc:
        append_report(artifact_dir, f"## QA Command Failure\n\n`{args.command}` failed: {exc}\n\n")
        print(f"QA command failed: {exc}", file=sys.stderr)
        return 1
    return 1


if __name__ == "__main__":
    sys.exit(main())
