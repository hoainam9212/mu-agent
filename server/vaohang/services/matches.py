"""Canh trận MU với ít request nhất có thể (hợp với gói Free).

Hang chỉ cần kết quả cuối trận, không cần diễn biến, nên:
- lịch trận: 1 request mỗi 12 tiếng;
- kết quả: bắt đầu hỏi ở phút 105 sau giờ bóng lăn, mỗi 3 phút một lần, đến khi có kết quả.
"""

from __future__ import annotations

import json
import logging
from datetime import datetime, timedelta

import httpx
from sqlalchemy import select
from sqlalchemy.orm import Session

from ..clock import Clock
from ..config import Settings
from ..models import Fixture, KeyValue, User
from ..notifier import Notifier
from ..providers import FixtureInfo, MatchProvider, ProviderError
from ..rules import mu_result
from .cave import CaveService, record

log = logging.getLogger(__name__)

REPLAY_SCENARIOS = {
    # Tỷ số thật (UEFA, 13/10/2026). Replay chỉ cần kết quả cuối trận.
    "atletico-2026-10-13": dict(opponent="Atlético Madrid", competition="UEFA Champions League", home=False,
                                mu_goals=0, opp_goals=2),
    "draw-demo": dict(opponent="Đội Demo", competition="Giao hữu demo", home=True, mu_goals=1, opp_goals=1),
    "win-demo": dict(opponent="Đội Demo", competition="Giao hữu demo", home=True, mu_goals=2, opp_goals=1),
}
REPLAY_MATCH_MINUTES = 95


def _kv_get(db: Session, key: str) -> str | None:
    row = db.get(KeyValue, key)
    return row.value if row else None


def _kv_set(db: Session, key: str, value: str) -> None:
    row = db.get(KeyValue, key)
    if row is None:
        db.add(KeyValue(key=key, value=value))
    else:
        row.value = value


class MatchService:
    def __init__(self, settings: Settings, clock: Clock, provider: MatchProvider, caves: CaveService,
                 notifier: Notifier):
        self.settings = settings
        self.clock = clock
        self.provider = provider
        self.caves = caves
        self.notifier = notifier

    def tick(self, db: Session) -> None:
        self.refresh_schedule(db)
        self.send_reminders(db)
        self.poll_results(db)
        self.finish_replays(db)

    # ---- Lịch và kết quả --------------------------------------------------------------------

    def _note_error(self, db: Session, error: Exception) -> None:
        log.warning("match provider error: %s", error)
        _kv_set(db, "last_provider_error", json.dumps({"at": self.clock.now().isoformat(), "error": str(error)}))
        db.commit()

    def upsert(self, db: Session, info: FixtureInfo, provider: str) -> Fixture:
        fixture = db.scalar(select(Fixture).where(Fixture.provider == provider, Fixture.provider_id == info.provider_id))
        if fixture is None:
            fixture = Fixture(provider=provider, provider_id=info.provider_id)
            db.add(fixture)
        if fixture.processed_at is None:
            fixture.kickoff = info.kickoff
            fixture.opponent = info.opponent
            fixture.competition = info.competition
            fixture.home = info.home
            fixture.status = info.status
            fixture.mu_goals = info.mu_goals
            fixture.opp_goals = info.opp_goals
            fixture.result = None if info.friendly else info.result
        db.flush()
        return fixture

    def _finalize(self, db: Session, fixture: Fixture) -> None:
        now = self.clock.now()
        if fixture.status != "FINISHED" or fixture.processed_at is not None:
            return
        if fixture.result is None or fixture.kickoff < now - timedelta(hours=self.settings.result_poll_giveup_h):
            # Giao hữu, hoặc trận cũ thấy được lúc mới khởi động: ghi nhận, không mở/đóng hang muộn.
            fixture.processed_at = now
            db.commit()
            return
        self.caves.apply_result(db, fixture)

    def refresh_schedule(self, db: Session, force: bool = False) -> None:
        if self.provider.name == "manual":
            return
        now = self.clock.now()
        last = _kv_get(db, "schedule_refreshed_at")
        if not force and last and now - datetime.fromisoformat(last) < timedelta(hours=self.settings.schedule_refresh_h):
            return
        try:
            infos = self.provider.schedule(now)
        except (ProviderError, httpx.HTTPError) as e:
            self._note_error(db, e)
            return
        for info in infos:
            self.upsert(db, info, self.provider.name)
        _kv_set(db, "schedule_refreshed_at", now.isoformat())
        db.commit()
        for fixture in db.scalars(select(Fixture).where(Fixture.status == "FINISHED", Fixture.processed_at.is_(None))):
            self._finalize(db, fixture)

    def poll_results(self, db: Session) -> None:
        if self.provider.name == "manual":
            return
        now = self.clock.now()
        s = self.settings
        due = db.scalars(
            select(Fixture).where(
                Fixture.provider == self.provider.name,
                Fixture.processed_at.is_(None),
                Fixture.status.in_(("SCHEDULED", "LIVE")),
                Fixture.kickoff <= now - timedelta(minutes=s.result_poll_start_min),
                Fixture.kickoff >= now - timedelta(hours=s.result_poll_giveup_h),
            )
        )
        for fixture in list(due):
            if fixture.last_polled_at and now - fixture.last_polled_at < timedelta(seconds=s.result_poll_interval_s):
                continue
            fixture.last_polled_at = now
            db.commit()
            try:
                info = self.provider.fixture(fixture.provider_id)
            except (ProviderError, httpx.HTTPError) as e:
                self._note_error(db, e)
                return
            if info is not None:
                self.upsert(db, info, self.provider.name)
                db.commit()
                self._finalize(db, fixture)

    def send_reminders(self, db: Session) -> None:
        """Nhắc trước trận. App chỉ hiện nếu giờ bóng lăn rơi vào đêm theo giờ máy."""
        now = self.clock.now()
        soon = db.scalars(
            select(Fixture).where(
                Fixture.provider != "replay",
                Fixture.reminded_at.is_(None),
                Fixture.status == "SCHEDULED",
                Fixture.kickoff > now,
                Fixture.kickoff <= now + timedelta(minutes=self.settings.reminder_minutes),
            )
        )
        for fixture in list(soon):
            fixture.reminded_at = now
            db.commit()
            payload = {
                "kind": "match_reminder",
                "fixture_id": str(fixture.id),
                "kickoff": fixture.kickoff.isoformat() + "Z",
                "opponent": fixture.opponent,
            }
            for user in db.scalars(select(User).where(User.fcm_token.is_not(None))):
                self.notifier.send(user, payload)

    # ---- Replay và nhập tay -------------------------------------------------------------------

    def start_replay(self, db: Session, scenario: str, speed: float) -> Fixture:
        if scenario not in REPLAY_SCENARIOS:
            raise KeyError(scenario)
        spec = REPLAY_SCENARIOS[scenario]
        now = self.clock.now()
        fixture = Fixture(
            provider="replay",
            provider_id=f"{scenario}-{now:%Y%m%d%H%M%S%f}",
            kickoff=now,
            opponent=spec["opponent"],
            competition=spec["competition"],
            home=spec["home"],
            status="LIVE",
            mu_goals=spec["mu_goals"],
            opp_goals=spec["opp_goals"],
            result=mu_result(spec["mu_goals"], spec["opp_goals"]),
            replay_ends_at=now + timedelta(minutes=REPLAY_MATCH_MINUTES / max(speed, 1)),
        )
        db.add(fixture)
        record(db, self.clock, "replay_started", scenario=scenario, speed=speed)
        db.commit()
        return fixture

    def finish_replays(self, db: Session) -> None:
        now = self.clock.now()
        done = db.scalars(
            select(Fixture).where(
                Fixture.provider == "replay", Fixture.processed_at.is_(None), Fixture.replay_ends_at <= now
            )
        )
        for fixture in list(done):
            fixture.status = "FINISHED"
            db.commit()
            self.caves.apply_result(db, fixture)

    def set_result(self, db: Session, fixture: Fixture, mu_goals: int, opp_goals: int,
                   mu_won_shootout: bool | None = None) -> Fixture:
        """Người trực nhập kết quả khi API lỗi. Áp dụng ngay, kể cả trận từ nhà cung cấp."""
        if fixture.processed_at is not None:
            raise ValueError("already_processed")
        fixture.status = "FINISHED"
        fixture.mu_goals, fixture.opp_goals = mu_goals, opp_goals
        fixture.result = mu_result(mu_goals, opp_goals, mu_won_shootout)
        record(db, self.clock, "manual_result", fixture_id=fixture.id, result=fixture.result)
        db.commit()
        self.caves.apply_result(db, fixture)
        return fixture
