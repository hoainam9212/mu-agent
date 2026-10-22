"""Hạn mức request mỗi ngày (UTC) cho nhà cung cấp dữ liệu trận."""

from __future__ import annotations

from typing import Callable

from sqlalchemy import select
from sqlalchemy.orm import Session, sessionmaker

from ..clock import Clock
from ..models import ApiUsage
from . import BudgetExceeded


class RequestBudget:
    def __init__(self, provider: str, daily_limit: int, sessions: sessionmaker, clock: Clock):
        self.provider = provider
        self.daily_limit = daily_limit
        self._sessions = sessions
        self._clock = clock

    def _row(self, db: Session) -> ApiUsage:
        day = self._clock.now().date().isoformat()
        row = db.scalar(select(ApiUsage).where(ApiUsage.provider == self.provider, ApiUsage.day == day))
        if row is None:
            row = ApiUsage(provider=self.provider, day=day, count=0)
            db.add(row)
        return row

    def used_today(self) -> int:
        with self._sessions() as db:
            return self._row(db).count

    def remaining(self) -> int:
        return max(0, self.daily_limit - self.used_today())

    def spend(self, call: Callable[[], object]):
        """Trừ 1 request rồi gọi. Hết hạn mức thì báo lỗi, không gọi."""
        with self._sessions() as db:
            row = self._row(db)
            if row.count >= self.daily_limit:
                raise BudgetExceeded(f"{self.provider}: đã dùng hết {self.daily_limit} request hôm nay")
            row.count += 1
            db.commit()
        return call()
