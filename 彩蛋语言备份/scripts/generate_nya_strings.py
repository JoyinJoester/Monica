#!/usr/bin/env python3
"""Generate the values-zh-rNY (喵喵语) string pack from values-zh.

Mechanism: values-zh-rNY is a fake-region variant (Locale("zh", "NY"), same
trick as the official en-rXA pseudo locale). Strings missing from it fall back
to values-zh, so this script only ever produces strings derived from the
Chinese source. Partial translations may use that Chinese fallback.

Rules:
- Short dock labels (nav_*_short) are omitted to retain the concise Chinese
  labels through resource fallback.
- Every sentence-final punctuation mark (。 ！ ？ … and half-width ! ?) gets
  喵 inserted right before it.
- Cores without sentence-final punctuation (buttons, labels) get a trailing 喵.
- Cores without any CJK character (brand names, English, bare placeholders,
  bare numbers) are left untouched.
- Lines marked translatable="false" are copied verbatim.
- Pack-wide vocabulary normalization runs on every output value (overrides
  included): the pronoun 你 becomes 您 and 回收站 becomes 纸箱, so the pack
  shares one voice. See scripts/nya_mapping.md.
- Raw line text is transformed, so Android escapes (\n, \', entities) and file
  formatting are preserved byte-for-byte outside the inserted characters.
- The prev-character guard makes the transform idempotent (re-running on an
  already-catified pack changes nothing).

Usage: python scripts/generate_nya_strings.py
"""

import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
RES_DIR = REPO_ROOT / "Monica for Android" / "app" / "src" / "main" / "res"
SOURCE_DIR = RES_DIR / "values-zh"
TARGET_DIR = RES_DIR / "values-zh-rNY"
OVERRIDE_FILE = REPO_ROOT / "scripts" / "nya_overrides.txt"

CJK_RE = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")
STRING_RE = re.compile(r'^(\s*)<string name="([^"]+)"((?:\s+[\w:.-]+="[^"]*")*)\s*>(.*)</string>(\s*<!--.*-->)?\s*$')
ITEM_RE = re.compile(r'^(\s*)<item((?:\s+[\w:.-]+="[^"]*")*)\s*>(.*)</item>(\s*<!--.*-->)?\s*$')
# Trailing markup that 喵 must be inserted in front of: whitespace, literal \n,
# real or escaped closing tags.
MARKUP_SUFFIX_RE = re.compile(r'((?:\s|\\n|</[A-Za-z0-9]+>|&lt;/[A-Za-z0-9]+&gt;)+)$')
SENTENCE_PUNCT = "。！？…!?"
PLACEHOLDER_RE = re.compile(r"""%(?:\d+\$)?[-+# 0,(]*(?:\d+|\*)?(?:\.\d+|\.\*)?[a-zA-Z]""")
BRANDS = ("Monica", "KeePass", "Bitwarden", "Steam", "WebDAV", "MDBX")
FORBIDDEN_RE = re.compile(r"[<>&'\"]")
# Pack-wide vocabulary normalization applied to every output value (overrides
# included): 人称统一为"您"（「迷你」里的"你"是词素不是人称，负向后顾豁免），
# 回收站在猫语里叫"纸箱"。See scripts/nya_mapping.md.
REPLACEMENTS = (
    (re.compile(r"(?<!迷)你"), "您"),
    (re.compile(r"回收站"), "纸箱"),
)


def load_overrides() -> dict:
    if not OVERRIDE_FILE.is_file():
        return {}
    overrides = {}
    for raw in OVERRIDE_FILE.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, _, text = line.partition("=")
        overrides[name.strip()] = text.strip()
    return overrides


def check_override(name: str, source: str, text: str) -> str | None:
    """Return a replacement using the hand-polished override, or None to reject it."""
    problem = None
    if FORBIDDEN_RE.search(text):
        problem = "contains forbidden XML characters"
    elif sorted(PLACEHOLDER_RE.findall(source)) != sorted(PLACEHOLDER_RE.findall(text)):
        problem = "placeholder mismatch"
    elif any(brand in source for brand in BRANDS) and not all(
        brand in text for brand in BRANDS if brand in source
    ):
        problem = "lost a brand word"
    if problem:
        print(f"  ! override rejected for {name}: {problem}", file=sys.stderr)
        return None
    return text


def nya_transform(raw: str) -> str:
    """Insert 喵 into one raw (still-escaped) string value."""
    suffix_match = MARKUP_SUFFIX_RE.search(raw)
    core, markup_suffix = (raw[: suffix_match.start()], suffix_match.group()) if suffix_match else (raw, "")
    colon_suffix = ""
    if core.endswith(":") or core.endswith("："):
        colon_suffix, core = core[-1], core[:-1]
    if not core or not CJK_RE.search(core):
        return raw

    out = []
    for index, char in enumerate(core):
        if char in SENTENCE_PUNCT:
            prev = core[index - 1] if index > 0 else ""
            if prev == "喵" or prev in SENTENCE_PUNCT:
                out.append(char)
            else:
                out.append("喵")
                out.append(char)
        else:
            out.append(char)

    result = "".join(out)
    if not result or (result[-1] not in SENTENCE_PUNCT and result[-1] != "喵"):
        result += "喵"
    return result + colon_suffix + markup_suffix


def normalize_vocabulary(text: str) -> str:
    for pattern, replacement in REPLACEMENTS:
        text = pattern.sub(replacement, text)
    return text


def transform_line(line: str, stats: dict) -> str:
    if line.endswith("\r\n"):
        body, eol = line[:-2], "\r\n"
    elif line.endswith("\n"):
        body, eol = line[:-1], "\n"
    else:
        body, eol = line, ""

    string_match = STRING_RE.match(body)
    if string_match:
        indent, name, attrs, value, comment = string_match.groups()
        if name.startswith("nav_") and name.endswith("_short"):
            return ""
        if 'translatable="false"' in attrs:
            return line
        transformed = None
        if name in stats["overrides"]:
            transformed = check_override(name, value, stats["overrides"][name])
            if transformed is not None:
                stats["override_applied"] += 1
            else:
                stats["override_rejected"] += 1
        if transformed is None:
            transformed = nya_transform(value)
        transformed = normalize_vocabulary(transformed)
        stats["transformed" if transformed != value else "kept"] += 1
        return f'{indent}<string name="{name}"{attrs}>{transformed}</string>{comment or ""}{eol}'

    item_match = ITEM_RE.match(body)
    if item_match:
        indent, attrs, value, comment = item_match.groups()
        if 'translatable="false"' in attrs:
            return line
        transformed = nya_transform(value)
        transformed = normalize_vocabulary(transformed)
        stats["transformed" if transformed != value else "kept"] += 1
        return f"{indent}<item{attrs}>{transformed}</item>{comment or ''}{eol}"

    if "<string " in body and "</string>" not in body:
        sys.exit(f"Unhandled multi-line <string> element in source: {body.strip()!r}")
    if "<item" in body and "</item>" not in body:
        sys.exit(f"Unhandled multi-line <item> element in source: {body.strip()!r}")
    return line


def main() -> None:
    if not SOURCE_DIR.is_dir():
        sys.exit(f"Missing source directory: {SOURCE_DIR}")
    files = sorted(SOURCE_DIR.glob("*.xml"))
    if not files:
        sys.exit(f"No XML resources under {SOURCE_DIR}")

    overrides = load_overrides()
    print(f"Loaded {len(overrides)} hand-polished overrides from {OVERRIDE_FILE.name}")

    TARGET_DIR.mkdir(parents=True, exist_ok=True)
    total = {"transformed": 0, "kept": 0, "override_applied": 0, "override_rejected": 0}
    matched_names = set()
    for source_file in files:
        raw = source_file.read_bytes().decode("utf-8")
        lines = raw.splitlines(keepends=True)
        before = dict(total)
        stats = dict(total, overrides=overrides)
        rebuilt = "".join(transform_line(line, stats) for line in lines)
        for key in total:
            total[key] = stats[key]
        string_match = STRING_RE.match
        for line in lines:
            match = string_match(line.rstrip("\r\n"))
            if match:
                matched_names.add(match.group(2))
        target_file = TARGET_DIR / source_file.name
        target_file.write_bytes(rebuilt.encode("utf-8"))
        print(
            f"{source_file.name}: {total['transformed'] - before['transformed']} catified, "
            f"{total['kept'] - before['kept']} kept, "
            f"{total['override_applied'] - before['override_applied']} overrides applied, "
            f"{total['override_rejected'] - before['override_rejected']} rejected"
        )

    orphan = sorted(set(overrides) - matched_names)
    if orphan:
        print(f"\n! {len(orphan)} overrides match no source string (typos?): {', '.join(orphan)}", file=sys.stderr)
    print(
        f"\nTotal: {total['transformed']} catified, {total['kept']} kept, "
        f"{total['override_applied']} overrides applied, {total['override_rejected']} rejected -> {TARGET_DIR}"
    )


if __name__ == "__main__":
    main()
