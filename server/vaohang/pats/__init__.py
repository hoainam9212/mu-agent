"""Câu vỗ vai: AI soạn theo đúng trận, lọc 2 lớp, thiếu thì lấy từ kho dự phòng."""

from __future__ import annotations

import logging
import random
from dataclasses import dataclass
from typing import Protocol

from sqlalchemy import select
from sqlalchemy.orm import Session

from ..models import Fixture, PatLine
from .bank import VERIFIED_FACTS, pick_bank_lines, scenario_tags
from .filters import allowed_numbers, check_line, names_from

log = logging.getLogger(__name__)

LINES_PER_FIXTURE = 8
CHOICES_PER_USER = 4


@dataclass
class PatContext:
    opponent: str
    competition: str
    score: str  # vd. "Atlético 2-0 MU"
    result: str  # L | D
    margin: int
    non_win_streak: int
    cave_days: int
    facts: list[str]

    @classmethod
    def for_fixture(cls, fixture: Fixture, non_win_streak: int, cave_days: int) -> "PatContext":
        margin = abs((fixture.mu_goals or 0) - (fixture.opp_goals or 0))
        return cls(
            opponent=fixture.opponent,
            competition=fixture.competition,
            score=fixture.score_text,
            result=fixture.result or "L",
            margin=margin,
            non_win_streak=non_win_streak,
            cave_days=cave_days,
            facts=list(VERIFIED_FACTS),
        )


class LineGenerator(Protocol):
    def generate(self, ctx: PatContext) -> list[str]: ...


def filter_lines(lines: list[str], ctx: PatContext) -> list[str]:
    names = names_from(ctx.opponent, ctx.competition, ctx.score)
    numbers = allowed_numbers(ctx.score, *ctx.facts)
    kept = []
    for line in lines:
        line = line.strip()
        reason = check_line(line, names, numbers)
        if reason:
            log.info("pat line rejected (%s): %s", reason, line)
        elif line not in kept:
            kept.append(line)
    return kept


def ensure_lines(db: Session, fixture: Fixture, ctx: PatContext, generator: LineGenerator | None) -> list[PatLine]:
    existing = list(db.scalars(select(PatLine).where(PatLine.fixture_id == fixture.id)))
    if existing:
        return existing
    ai_lines: list[str] = []
    if generator is not None:
        try:
            ai_lines = filter_lines(generator.generate(ctx), ctx)[:LINES_PER_FIXTURE]
        except Exception:  # AI hỏng thì vẫn còn kho dự phòng
            log.exception("pat generation failed")
    rows = [PatLine(fixture_id=fixture.id, text=t, source="ai") for t in ai_lines]
    missing = LINES_PER_FIXTURE - len(rows)
    if missing > 0:
        tags = scenario_tags(ctx.result, ctx.margin, ctx.non_win_streak, ctx.cave_days)
        bank = pick_bank_lines(tags, ctx.opponent, ctx.score, limit=LINES_PER_FIXTURE, seed=fixture.id)
        bank = [t for t in filter_lines(bank, ctx) if t not in ai_lines]
        rows += [PatLine(fixture_id=fixture.id, text=t, source="bank") for t in bank[:missing]]
    db.add_all(rows)
    db.flush()
    return rows


def choices_for(lines: list[PatLine], user_id: int, fixture_id: int) -> list[PatLine]:
    rng = random.Random(f"{user_id}:{fixture_id}")
    return rng.sample(lines, min(CHOICES_PER_USER, len(lines)))
