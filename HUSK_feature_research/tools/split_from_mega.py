#!/usr/bin/env python3
"""Split HUSK_Feature_Research_MEGA.md into HUSK_feature_research/<category>/*.md."""

from __future__ import annotations

import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
MEGA = REPO_ROOT / "HUSK_Feature_Research_MEGA.md"
OUT_ROOT = REPO_ROOT / "HUSK_feature_research"

SECTION_START = re.compile(r"^## (\d+)\. (.+)$")
ADDENDUM_START = re.compile(r"^## (Addendum:.+)$")

FOLDER_BY_SECTION: dict[int, str] = {
    1: "core-architecture-and-models",
    2: "intelligence-and-tool-use",
    3: "intelligence-and-tool-use",
    4: "inference-optimization",
    5: "reference",
    6: "core-architecture-and-models",
    7: "core-architecture-and-models",
    8: "proactive-and-autonomous",
    9: "intelligence-and-tool-use",
    10: "intelligence-and-tool-use",
    11: "multimodal-and-creative",
    12: "platform-integration",
    13: "on-device-intelligence",
    14: "inference-optimization",
    15: "proactive-and-autonomous",
    16: "proactive-and-autonomous",
    17: "privacy-ux-developer",
    18: "privacy-ux-developer",
    19: "privacy-ux-developer",
    20: "privacy-ux-developer",
    21: "privacy-ux-developer",
    22: "reference",
    24: "intelligence-and-tool-use",
    25: "intelligence-and-tool-use",
    26: "multimodal-and-creative",
    27: "multimodal-and-creative",
    28: "on-device-intelligence",
    29: "on-device-intelligence",
    30: "on-device-intelligence",
    31: "on-device-intelligence",
    32: "on-device-intelligence",
    33: "on-device-intelligence",
    34: "platform-integration",
    35: "platform-integration",
    36: "platform-integration",
    37: "platform-integration",
    38: "platform-integration",
    39: "platform-integration",
    40: "inference-optimization",
    41: "inference-optimization",
    42: "inference-optimization",
    43: "inference-optimization",
    44: "inference-optimization",
    45: "inference-optimization",
    46: "inference-optimization",
    47: "inference-optimization",
    48: "inference-optimization",
    49: "quality-and-standards",
    50: "reference",
    51: "reference",
}

CATEGORY_LABELS: dict[str, str] = {
    "core-architecture-and-models": "Core architecture and models",
    "intelligence-and-tool-use": "Intelligence and tool use",
    "proactive-and-autonomous": "Proactive and autonomous features",
    "multimodal-and-creative": "Multimodal and creative",
    "on-device-intelligence": "On-device intelligence",
    "platform-integration": "Platform integration",
    "inference-optimization": "Inference optimization",
    "quality-and-standards": "Quality and standards",
    "privacy-ux-developer": "Privacy, UX, and developer",
    "reference": "Reference",
}


def slugify(title: str) -> str:
    s = title.lower().replace("&", "and")
    s = s.replace("—", "-")
    s = re.sub(r"[^a-z0-9]+", "-", s)
    return s.strip("-")


def parse_sections(lines: list[str]) -> list[tuple[str, int | None, str, list[str]]]:
    """Return list of (kind, num|None, title, body_lines including heading)."""
    sections: list[tuple[str, int | None, str, list[str]]] = []
    i = 0
    while i < len(lines) and not lines[i].startswith("## 1. "):
        i += 1
    while i < len(lines):
        line = lines[i].rstrip("\n")
        m_sec = SECTION_START.match(line)
        m_add = ADDENDUM_START.match(line)
        if m_sec:
            num = int(m_sec.group(1))
            title = m_sec.group(2)
            body = [lines[i]]
            i += 1
            while i < len(lines):
                nxt = lines[i].rstrip("\n")
                if SECTION_START.match(nxt) or ADDENDUM_START.match(nxt):
                    break
                body.append(lines[i])
                i += 1
            sections.append(("section", num, title, body))
            continue
        if m_add:
            title = m_add.group(1)
            body = [lines[i]]
            i += 1
            while i < len(lines):
                nxt = lines[i].rstrip("\n")
                if SECTION_START.match(nxt) or ADDENDUM_START.match(nxt):
                    break
                body.append(lines[i])
                i += 1
            sections.append(("addendum", None, title, body))
            continue
        i += 1
    return sections


def body_without_heading(body: list[str]) -> list[str]:
    return body[1:] if body else []


def write_merged_priority_matrix(
    path: Path, body_22: list[str], body_50: list[str]
) -> None:
    parts = [
        "# Implementation Priority Matrix\n\n",
        "## Summary matrix (original §22)\n\n",
        *body_without_heading(body_22),
        "\n---\n\n",
        "## Expanded matrix (§50)\n\n",
        *body_without_heading(body_50),
    ]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(parts), encoding="utf-8")


def main() -> None:
    if not MEGA.is_file():
        raise SystemExit(f"Missing mega file: {MEGA}")

    raw = MEGA.read_text(encoding="utf-8")
    lines = raw.splitlines(keepends=True)
    sections = parse_sections(lines)

    written: list[tuple[str, Path]] = []
    pending_22: list[str] | None = None

    for kind, num, title, body in sections:
        if kind == "addendum":
            rel = Path("reference") / "addendum-deep-dive-expansions.md"
            path = OUT_ROOT / rel
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("".join(body), encoding="utf-8")
            written.append(("reference", rel))
            continue

        assert num is not None
        if num == 22:
            pending_22 = body
            continue
        if num == 50:
            if pending_22 is None:
                raise SystemExit("§50 found before §22; cannot merge priority matrix")
            rel = Path("reference") / "implementation-priority-matrix.md"
            write_merged_priority_matrix(OUT_ROOT / rel, pending_22, body)
            written.append(("reference", rel))
            pending_22 = None
            continue

        folder = FOLDER_BY_SECTION.get(num)
        if folder is None:
            raise SystemExit(f"No folder mapping for section {num}")

        slug = slugify(title)
        filename = f"{num:02d}-{slug}.md"
        rel = Path(folder) / filename
        path = OUT_ROOT / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("".join(body), encoding="utf-8")
        written.append((folder, rel))

    if pending_22 is not None:
        raise SystemExit("§22 was not merged with §50")

    by_cat: dict[str, list[Path]] = {}
    for folder, rel in written:
        by_cat.setdefault(folder, []).append(rel)
    for k in by_cat:
        by_cat[k].sort(key=lambda p: p.as_posix())

    meta_lines = [
        "# HUSK feature research (split)\n\n",
        "This folder breaks [HUSK_Feature_Research_MEGA.md](../HUSK_Feature_Research_MEGA.md) "
        "into category subfolders. The monolithic mega file is unchanged and remains the "
        "full single-file copy.\n\n",
        "**Date:** April 13, 2026 (Expanded from April 12, 2026 original)\n\n",
        "**Target repo:** [github.com/riley1802/HUSK](https://github.com/riley1802/HUSK)\n\n",
        "**Stack:** Kotlin/Android · Google AI Edge · LiteRT / LiteRT-LM · "
        "Hugging Face Integration · Gemma 4 E2B/E4B\n\n",
        "**Upstream:** [google-ai-edge/gallery](https://github.com/google-ai-edge/gallery)\n\n",
        "**Target device:** Samsung Galaxy Z Fold 7 · Snapdragon 8 Elite · 12 GB RAM\n\n",
        "---\n\n",
        "## Documents by category\n\n",
    ]

    cat_order = [
        "core-architecture-and-models",
        "intelligence-and-tool-use",
        "proactive-and-autonomous",
        "multimodal-and-creative",
        "on-device-intelligence",
        "platform-integration",
        "inference-optimization",
        "quality-and-standards",
        "privacy-ux-developer",
        "reference",
    ]
    for cat in cat_order:
        paths = by_cat.get(cat, [])
        if not paths:
            continue
        label = CATEGORY_LABELS[cat]
        meta_lines.append(f"### {label}\n\n")
        for rel in paths:
            meta_lines.append(f"- [{rel.as_posix()}]({rel.as_posix()})\n")
        meta_lines.append("\n")

    readme_path = OUT_ROOT / "README.md"
    readme_path.write_text("".join(meta_lines), encoding="utf-8")
    print(f"Wrote {len(written)} topic files + README under {OUT_ROOT}")


if __name__ == "__main__":
    main()
