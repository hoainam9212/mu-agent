"""Luật hang: mở/đóng theo kết quả trận, vào/ra theo người dùng, sáng/tắt đèn theo agent."""

from __future__ import annotations

import json
import logging
from datetime import datetime, timedelta

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..clock import Clock
from ..config import Settings
from ..models import (
    Cave,
    Fixture,
    Group,
    GroupMember,
    Invite,
    LightWindow,
    Membership,
    Pat,
    PatLine,
    Presence,
    TelemetryEvent,
    User,
)
from ..notifier import Notifier
from ..pats import LineGenerator, PatContext, choices_for, ensure_lines
from ..rules import WIN, lights_on, next_cave_open

log = logging.getLogger(__name__)


class CaveError(Exception):
    def __init__(self, code: str, status: int = 400):
        super().__init__(code)
        self.code = code
        self.status = status


def record(db: Session, clock: Clock, type_: str, user_id: int | None = None, **props) -> None:
    db.add(TelemetryEvent(user_id=user_id, type=type_, ts=clock.now(), props=json.dumps(props, ensure_ascii=False)))


def approved_members(db: Session, group_id: int) -> list[User]:
    return list(
        db.scalars(
            select(User)
            .join(GroupMember, GroupMember.user_id == User.id)
            .where(GroupMember.group_id == group_id, GroupMember.status == "approved")
            .order_by(GroupMember.joined_at)
        )
    )


def open_cave(db: Session, group_id: int) -> Cave | None:
    return db.scalar(select(Cave).where(Cave.group_id == group_id, Cave.status == "OPEN"))


def inside_memberships(db: Session, cave_id: int) -> list[Membership]:
    return list(
        db.scalars(
            select(Membership)
            .where(Membership.cave_id == cave_id, Membership.left_at.is_(None))
            .order_by(Membership.entered_at)
        )
    )


class CaveService:
    def __init__(self, settings: Settings, clock: Clock, notifier: Notifier, generator: LineGenerator | None):
        self.settings = settings
        self.clock = clock
        self.notifier = notifier
        self.generator = generator

    # ---- Kết quả trận ---------------------------------------------------------------------

    def non_win_streak(self, db: Session, fixture: Fixture) -> int:
        results = db.scalars(
            select(Fixture.result)
            .where(
                Fixture.result.is_not(None),
                Fixture.kickoff <= fixture.kickoff,
                (Fixture.provider == "replay") if fixture.is_demo else (Fixture.provider != "replay"),
            )
            .order_by(Fixture.kickoff.desc())
        )
        streak = 0
        for result in results:
            if result == WIN:
                break
            streak += 1
        return streak

    def apply_result(self, db: Session, fixture: Fixture) -> None:
        """Gọi đúng một lần khi trận kết thúc. Gửi thông báo sau khi đã commit."""
        if fixture.processed_at is not None or fixture.result is None:
            return
        now = self.clock.now()
        outgoing: list[tuple[User, dict]] = []
        windows = 0
        earliest_open: datetime | None = None
        groups = db.scalars(select(Group).where(Group.is_demo == fixture.is_demo))
        for group in groups:
            cave = open_cave(db, group.id)
            is_open = cave is not None
            open_after = next_cave_open(is_open, fixture.result)
            if not is_open and open_after:
                cave = Cave(group_id=group.id, status="OPEN", opened_at=now, opened_by_fixture_id=fixture.id)
                db.add(cave)
                db.flush()
                record(db, self.clock, "cave_opened", group_id=group.id, cave_id=cave.id, fixture_id=fixture.id)
            elif is_open and not open_after:
                cave.status, cave.closed_at, cave.closed_by_fixture_id = "CLOSED", now, fixture.id
                for m in inside_memberships(db, cave.id):
                    m.left_at, m.left_reason = now, "win"
                    record(db, self.clock, "cave_leave", m.user_id, cave_id=cave.id, reason="win")
                    outgoing.append(
                        (
                            db.get(User, m.user_id),
                            {"kind": "cave_exit", "cave_id": str(cave.id), "score": fixture.score_text},
                        )
                    )
                record(db, self.clock, "cave_closed", group_id=group.id, cave_id=cave.id, fixture_id=fixture.id)
            if lights_on(open_after, fixture.result):
                window = LightWindow(
                    cave_id=cave.id,
                    fixture_id=fixture.id,
                    starts_at=now,
                    ends_at=now + timedelta(minutes=self.settings.light_minutes),
                )
                db.add(window)
                windows += 1
                earliest_open = min(earliest_open or cave.opened_at, cave.opened_at)
                record(db, self.clock, "lights_on", group_id=group.id, cave_id=cave.id, fixture_id=fixture.id)
                inside_ids = [m.user_id for m in inside_memberships(db, cave.id)]
                inside_names = [db.get(User, uid).nickname for uid in inside_ids]
                for member in approved_members(db, group.id):
                    outgoing.append(
                        (
                            member,
                            {
                                "kind": "cave_lights_on",
                                "cave_id": str(cave.id),
                                "group_name": group.name,
                                "fixture_id": str(fixture.id),
                                "result": fixture.result,
                                "score": fixture.score_text,
                                "lights_until": window.ends_at.isoformat() + "Z",
                                "you_inside": "1" if member.id in inside_ids else "0",
                                # Chỉ hiện người đang trong hang, không điểm danh ai ở ngoài.
                                "inside_names": [n for uid, n in zip(inside_ids, inside_names) if uid != member.id],
                            },
                        )
                    )
        if windows:
            # Soạn câu trước khi báo, để ai mở hang cũng thấy ngay câu vỗ vai.
            cave_days = (now - earliest_open).days if earliest_open else 0
            ctx = PatContext.for_fixture(fixture, self.non_win_streak(db, fixture), cave_days)
            ensure_lines(db, fixture, ctx, self.generator)
        fixture.processed_at = now
        db.commit()
        for user, data in outgoing:
            self.notifier.send(user, data)

    # ---- Hành động của người dùng ------------------------------------------------------------

    def _cave_for_member(self, db: Session, user: User, cave_id: int) -> Cave:
        cave = db.get(Cave, cave_id)
        if cave is None:
            raise CaveError("cave_not_found", 404)
        member = db.scalar(
            select(GroupMember).where(
                GroupMember.group_id == cave.group_id,
                GroupMember.user_id == user.id,
                GroupMember.status == "approved",
            )
        )
        if member is None:
            raise CaveError("not_member", 403)
        return cave

    def _membership(self, db: Session, user: User, cave: Cave) -> Membership | None:
        return db.scalar(
            select(Membership).where(
                Membership.cave_id == cave.id, Membership.user_id == user.id, Membership.left_at.is_(None)
            )
        )

    def latest_window(self, db: Session, cave: Cave) -> LightWindow | None:
        return db.scalar(
            select(LightWindow).where(LightWindow.cave_id == cave.id).order_by(LightWindow.starts_at.desc()).limit(1)
        )

    def active_window(self, db: Session, cave: Cave) -> LightWindow | None:
        window = self.latest_window(db, cave)
        if window and cave.status == "OPEN" and window.ends_at > self.clock.now():
            return window
        return None

    def enter(self, db: Session, user: User, cave_id: int, source: str = "app") -> Membership:
        cave = self._cave_for_member(db, user, cave_id)
        if cave.status != "OPEN":
            raise CaveError("cave_closed", 409)
        membership = self._membership(db, user, cave)
        if membership is None:
            membership = Membership(cave_id=cave.id, user_id=user.id, entered_at=self.clock.now())
            db.add(membership)
            record(db, self.clock, "cave_enter", user.id, cave_id=cave.id, source=source)
            db.commit()
        return membership

    def leave(self, db: Session, user: User, cave_id: int) -> None:
        """Ra lặng lẽ: không báo cho ai."""
        cave = self._cave_for_member(db, user, cave_id)
        membership = self._membership(db, user, cave)
        if membership is not None:
            membership.left_at, membership.left_reason = self.clock.now(), "self"
            record(db, self.clock, "cave_leave", user.id, cave_id=cave.id, reason="self")
            db.commit()

    def invite(self, db: Session, user: User, cave_id: int) -> int:
        cave = self._cave_for_member(db, user, cave_id)
        if self._membership(db, user, cave) is None:
            raise CaveError("not_inside", 409)
        window = self.active_window(db, cave)
        if window is None:
            raise CaveError("lights_off", 409)
        if db.scalar(select(Invite).where(Invite.window_id == window.id, Invite.from_user_id == user.id)):
            raise CaveError("already_invited", 409)
        inside = {m.user_id for m in inside_memberships(db, cave.id)}
        awake = set(
            db.scalars(
                select(Presence.user_id).where(Presence.fixture_id == window.fixture_id, Presence.state == "awake")
            )
        )
        targets = [u for u in approved_members(db, cave.group_id) if u.id not in inside and u.id in awake]
        db.add(Invite(window_id=window.id, from_user_id=user.id, sent_at=self.clock.now(), recipients=len(targets)))
        record(db, self.clock, "invite_sent", user.id, cave_id=cave.id, recipients=len(targets))
        db.commit()
        for target in targets:
            self.notifier.send(
                target, {"kind": "cave_invite", "cave_id": str(cave.id), "from_name": user.nickname}
            )
        return len(targets)

    def send_pat(self, db: Session, user: User, cave_id: int, line_id: int) -> Pat:
        cave = self._cave_for_member(db, user, cave_id)
        if self._membership(db, user, cave) is None:
            raise CaveError("not_inside", 409)
        window = self.active_window(db, cave)
        if window is None:
            raise CaveError("lights_off", 409)
        line = db.get(PatLine, line_id)
        if line is None or line.fixture_id != window.fixture_id:
            raise CaveError("bad_line", 400)
        sent = db.scalar(
            select(func.count(Pat.id)).where(Pat.window_id == window.id, Pat.from_user_id == user.id)
        )
        if sent >= self.settings.max_pats_per_window:
            raise CaveError("too_many_pats", 429)
        pat = Pat(window_id=window.id, from_user_id=user.id, line_id=line.id, sent_at=self.clock.now())
        db.add(pat)
        record(db, self.clock, "pat_sent", user.id, cave_id=cave.id, source=line.source)
        db.commit()
        for m in inside_memberships(db, cave.id):
            if m.user_id != user.id:
                self.notifier.send(
                    db.get(User, m.user_id),
                    {"kind": "cave_pat", "cave_id": str(cave.id), "from_name": user.nickname, "text": line.text},
                )
        return pat

    def record_presence(self, db: Session, user: User, fixture_id: int, state: str) -> None:
        if state not in ("awake", "asleep", "busy"):
            raise CaveError("bad_state", 400)
        if db.get(Fixture, fixture_id) is None:
            raise CaveError("fixture_not_found", 404)
        row = db.scalar(select(Presence).where(Presence.user_id == user.id, Presence.fixture_id == fixture_id))
        if row is None:
            db.add(Presence(user_id=user.id, fixture_id=fixture_id, state=state, reported_at=self.clock.now()))
        else:
            row.state, row.reported_at = state, self.clock.now()
        db.commit()

    def view(self, db: Session, user: User, cave_id: int) -> dict:
        cave = self._cave_for_member(db, user, cave_id)
        me_inside = self._membership(db, user, cave) is not None
        inside = [db.get(User, m.user_id) for m in inside_memberships(db, cave.id)]
        window = self.latest_window(db, cave)
        active = self.active_window(db, cave)
        data: dict = {
            "id": cave.id,
            "group_id": cave.group_id,
            "status": cave.status,
            "opened_at": cave.opened_at.isoformat() + "Z",
            "me_inside": me_inside,
            # Chỉ hiện người đang trong hang.
            "inside": [{"id": u.id, "nickname": u.nickname} for u in inside],
            "lights": {
                "on": active is not None,
                "until": window.ends_at.isoformat() + "Z" if window else None,
                "fixture_id": window.fixture_id if window else None,
            },
            "pats": [],
            "choices": [],
        }
        if window and me_inside:
            fixture = db.get(Fixture, window.fixture_id)
            data["score"] = fixture.score_text
            pats = db.scalars(select(Pat).where(Pat.window_id == window.id).order_by(Pat.sent_at))
            data["pats"] = [
                {
                    "from": db.get(User, p.from_user_id).nickname,
                    "text": db.get(PatLine, p.line_id).text,
                    "at": p.sent_at.isoformat() + "Z",
                }
                for p in pats
            ]
            if active:
                lines = list(db.scalars(select(PatLine).where(PatLine.fixture_id == window.fixture_id)))
                data["choices"] = [{"id": l.id, "text": l.text} for l in choices_for(lines, user.id, window.fixture_id)]
        return data
