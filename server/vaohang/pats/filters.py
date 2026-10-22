"""Bộ lọc luật cho câu vỗ vai (lớp 1, không cần AI)."""

from __future__ import annotations

import re

MAX_LEN = 120

# Từ gợi ý chê/đổ lỗi hoặc nhắm vào người. Thà loại nhầm còn hơn lọt.
BLAME_WORDS = [
    "trọng tài", "thằng", "đồ", "ngu", "phế", "đổ lỗi", "lỗi của", "tại vì", "hlv", "huấn luyện viên",
    "thủ môn", "hậu vệ", "tiền đạo", "tiền vệ", "var", "cđv", "fan đội", "fan bên", "sa thải", "out",
    "ăn tiền", "bán độ", "kém cỏi", "tệ hại",
]
_BLAME = re.compile("|".join(rf"(?<!\w){re.escape(w)}(?!\w)" for w in BLAME_WORDS), re.UNICODE)

BASE_ALLOWED_NAMES = {
    "mu", "manchester", "united", "quỷ", "đỏ", "old", "trafford", "ngoại", "hạng", "anh",
    "champions", "league", "europa", "cúp", "premier", "c1", "c2",
}

_WORD = re.compile(r"\w+", re.UNICODE)
_SENTENCE_START = re.compile(r"(^|[.!?…:\"“”'\n]\s*)$")


def allowed_numbers(*texts: str) -> set[str]:
    numbers: set[str] = set()
    for text in texts:
        for n in re.findall(r"\d+", text):
            numbers.add(n)
            if len(n) == 4:
                numbers.add(n[2:])  # 2024-25 ↔ 24-25
    return numbers


def has_blame(line: str) -> bool:
    return _BLAME.search(line.lower()) is not None


def unknown_proper_names(line: str, allowed: set[str]) -> list[str]:
    """Từ viết hoa giữa câu mà không nằm trong danh sách cho phép → có thể là tên người."""
    names = []
    for match in _WORD.finditer(line):
        word = match.group()
        if not word[0].isupper() or word.isdigit():
            continue
        if _SENTENCE_START.search(line[: match.start()]):
            continue
        if word.lower() not in allowed:
            names.append(word)
    return names


def check_line(line: str, allowed_names: set[str], numbers: set[str]) -> str | None:
    """Trả về lý do loại, hoặc None nếu câu qua được."""
    if not line or len(line) > MAX_LEN:
        return "length"
    if has_blame(line):
        return "blame"
    if unknown_proper_names(line, allowed_names):
        return "name"
    if any(n not in numbers for n in re.findall(r"\d+", line)):
        return "number"
    return None


def names_from(*texts: str) -> set[str]:
    return BASE_ALLOWED_NAMES | {w.lower() for t in texts for w in _WORD.findall(t)}
