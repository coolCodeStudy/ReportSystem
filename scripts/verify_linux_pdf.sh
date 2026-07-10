#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "Usage: $0 <input.docx> [output-directory]" >&2
    exit 2
fi

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
input_path="$1"
if [[ ! -f "$input_path" ]]; then
    echo "DOCX not found: $input_path" >&2
    exit 2
fi

output_dir="${2:-$repo_root/build/pdf-linux-proof}"
mkdir -p "$output_dir"
output_dir="$(cd "$output_dir" && pwd)"
cp "$input_path" "$output_dir/input.docx"

image_name="reportsystem-pdf-linux-proof"
docker build --platform linux/amd64 -t "$image_name" -f "$repo_root/qa/pdf-linux/Dockerfile" "$repo_root"
docker run --rm --platform linux/amd64 \
    --user "$(id -u):$(id -g)" \
    --env HOME=/tmp \
    --volume "$output_dir:/work" \
    "$image_name" \
    bash -lc '
        set -euo pipefail
        profile_dir="$(mktemp -d /tmp/reportsystem-lo-profile.XXXXXX)"
        trap '\''rm -rf "$profile_dir"'\'' EXIT
        libreoffice --headless \
            -env:UserInstallation="file://$profile_dir" \
            --convert-to pdf \
            --outdir /work \
            /work/input.docx
        mv /work/input.pdf /work/report.pdf
        pdfinfo /work/report.pdf > /work/pdfinfo.txt
        pdffonts /work/report.pdf > /work/pdffonts.txt
        pdftotext -layout /work/report.pdf /work/text.txt
        pdftoppm -png -r 120 /work/report.pdf /work/page
    '

if [[ ! -s "$output_dir/report.pdf" ]]; then
    echo "Linux conversion did not produce a non-empty PDF." >&2
    exit 1
fi

echo "Linux PDF proof written to: $output_dir"

