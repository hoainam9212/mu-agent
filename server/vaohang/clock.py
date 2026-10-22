"""Đồng hồ thay được trong test. Mọi thời điểm trong hệ thống là UTC không kèm tzinfo."""

from __future__ import annotations

from datetime import datetime, timezone


def utcnow() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


class Clock:
    def now(self) -> datetime:
        return utcnow()


class FakeClock(Clock):
    def __init__(self, start: datetime):
        self._now = start

    def now(self) -> datetime:
        return self._now

    def advance(self, **kwargs) -> None:
        from datetime import timedelta

        self._now += timedelta(**kwargs)

    def set(self, value: datetime) -> None:
        self._now = value
