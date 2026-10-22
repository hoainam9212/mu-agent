"""Nguồn dữ liệu trận đấu của MU."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol


class ProviderError(Exception):
    pass


class BudgetExceeded(ProviderError):
    pass


@dataclass
class FixtureInfo:
    provider_id: str
    kickoff: datetime  # UTC, không kèm tzinfo
    opponent: str
    competition: str
    home: bool
    status: str  # SCHEDULED | LIVE | FINISHED | POSTPONED | CANCELLED
    mu_goals: int | None = None
    opp_goals: int | None = None
    result: str | None = None  # W | D | L khi FINISHED
    friendly: bool = False


class MatchProvider(Protocol):
    name: str

    def schedule(self, now: datetime) -> list[FixtureInfo]:
        """Các trận vài ngày gần đây và sắp tới."""

    def fixture(self, provider_id: str) -> FixtureInfo | None:
        """Trạng thái mới nhất của một trận."""


class ManualProvider:
    """Không gọi API ngoài; trận và kết quả nhập qua admin API."""

    name = "manual"

    def schedule(self, now: datetime) -> list[FixtureInfo]:
        return []

    def fixture(self, provider_id: str) -> FixtureInfo | None:
        return None


def parse_iso_utc(value: str) -> datetime:
    from datetime import timezone

    dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if dt.tzinfo is not None:
        dt = dt.astimezone(timezone.utc).replace(tzinfo=None)
    return dt
