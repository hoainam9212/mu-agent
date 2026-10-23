"""HTTP API cho app Android và cho người trực."""

from __future__ import annotations

import hashlib
import json
import secrets
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Header, HTTPException, Request
from pydantic import BaseModel, Field
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from ..models import Cave, Fixture, Group, KeyValue, TelemetryEvent, User
from ..notifier import OutboxNotifier
from ..services import groups as group_service
from ..services.cave import CaveError

router = APIRouter(prefix="/v1")

CLIENT_EVENTS = {
    "invite_shown", "cave_enter_tapped", "not_tonight_tapped", "lights_out_shown", "screen_off_after_ft",
    "exit_celebration_opened", "survey_answer", "reminder_shown", "checkin",
}


def _hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def get_db(request: Request):
    with request.app.state.container.sessions() as db:
        yield db


def get_container(request: Request):
    return request.app.state.container


def current_user(authorization: str = Header(default=""), db: Session = Depends(get_db)) -> User:
    token = authorization.removeprefix("Bearer ").strip()
    user = db.scalar(select(User).where(User.token_hash == _hash(token))) if token else None
    if user is None:
        raise HTTPException(401, "unauthorized")
    return user


def require_admin(request: Request, x_admin_token: str = Header(default="")) -> None:
    expected = request.app.state.container.settings.admin_token
    if not expected or not secrets.compare_digest(x_admin_token, expected):
        raise HTTPException(403, "admin_only")


def _run(fn, *args, **kwargs):
    try:
        return fn(*args, **kwargs)
    except CaveError as e:
        raise HTTPException(e.status, e.code)


# ---- Người dùng ---------------------------------------------------------------------------


class NewUser(BaseModel):
    nickname: str = Field(min_length=1, max_length=40)
    fcm_token: str | None = None


@router.post("/users")
def create_user(body: NewUser, db: Session = Depends(get_db), c=Depends(get_container)):
    token = secrets.token_urlsafe(32)
    user = User(nickname=body.nickname.strip(), token_hash=_hash(token), fcm_token=body.fcm_token,
                created_at=c.clock.now())
    db.add(user)
    db.commit()
    return {"user_id": user.id, "token": token}


class UpdateMe(BaseModel):
    nickname: str | None = Field(default=None, min_length=1, max_length=40)
    fcm_token: str | None = None


@router.patch("/me")
def update_me(body: UpdateMe, user: User = Depends(current_user), db: Session = Depends(get_db)):
    if body.nickname is not None:
        user.nickname = body.nickname.strip()
    if body.fcm_token is not None:
        user.fcm_token = body.fcm_token
    db.add(user)
    db.commit()
    return {"ok": True}


@router.get("/me")
def me(user: User = Depends(current_user), db: Session = Depends(get_db)):
    return {"id": user.id, "nickname": user.nickname, "groups": group_service.groups_for(db, user)}


# ---- Hội anh em ---------------------------------------------------------------------------


class NewGroup(BaseModel):
    name: str = Field(min_length=1, max_length=60)


@router.post("/groups")
def create_group(body: NewGroup, user: User = Depends(current_user), db: Session = Depends(get_db),
                 c=Depends(get_container)):
    group = group_service.create_group(db, c.clock, user, body.name)
    return {"id": group.id, "name": group.name, "invite_code": group.invite_code}


class JoinGroup(BaseModel):
    invite_code: str = Field(min_length=6, max_length=6)


@router.post("/groups/join")
def join_group(body: JoinGroup, user: User = Depends(current_user), db: Session = Depends(get_db),
               c=Depends(get_container)):
    member = _run(group_service.request_join, db, c.clock, user, body.invite_code)
    return {"group_id": member.group_id, "status": member.status}


@router.get("/groups")
def list_groups(user: User = Depends(current_user), db: Session = Depends(get_db)):
    return group_service.groups_for(db, user)


@router.post("/groups/{group_id}/members/{user_id}/approve")
def approve_member(group_id: int, user_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    _run(group_service.approve, db, user, group_id, user_id)
    return {"ok": True}


@router.delete("/groups/{group_id}/members/me")
def leave_group(group_id: int, user: User = Depends(current_user), db: Session = Depends(get_db),
                c=Depends(get_container)):
    group_service.leave_group(db, c.clock, user, group_id)
    return {"ok": True}


# ---- Trận, thức/ngủ, hang -----------------------------------------------------------------


def _fixture_json(f: Fixture) -> dict:
    return {
        "id": f.id,
        "kickoff": f.kickoff.isoformat() + "Z",
        "opponent": f.opponent,
        "competition": f.competition,
        "home": f.home,
        "status": f.status,
        "score": f.score_text,
        "result": f.result,
    }


@router.get("/matches/next")
def next_match(db: Session = Depends(get_db), c=Depends(get_container)):
    fixture = db.scalar(
        select(Fixture)
        .where(Fixture.provider != "replay", Fixture.status.in_(("SCHEDULED", "LIVE")),
               Fixture.kickoff >= c.clock.now().replace(hour=0, minute=0))
        .order_by(Fixture.kickoff)
        .limit(1)
    )
    return _fixture_json(fixture) if fixture else None


class PresenceIn(BaseModel):
    fixture_id: int
    state: str


@router.post("/presence")
def presence(body: PresenceIn, user: User = Depends(current_user), db: Session = Depends(get_db),
             c=Depends(get_container)):
    _run(c.caves.record_presence, db, user, body.fixture_id, body.state)
    return {"ok": True}


@router.get("/caves/{cave_id}")
def view_cave(cave_id: int, user: User = Depends(current_user), db: Session = Depends(get_db),
              c=Depends(get_container)):
    return _run(c.caves.view, db, user, cave_id)


class EnterIn(BaseModel):
    source: str = "app"  # notification | invite | app


@router.post("/caves/{cave_id}/enter")
def enter_cave(cave_id: int, body: EnterIn | None = None, user: User = Depends(current_user),
               db: Session = Depends(get_db), c=Depends(get_container)):
    _run(c.caves.enter, db, user, cave_id, (body or EnterIn()).source)
    return _run(c.caves.view, db, user, cave_id)


@router.post("/caves/{cave_id}/leave")
def leave_cave(cave_id: int, user: User = Depends(current_user), db: Session = Depends(get_db),
               c=Depends(get_container)):
    _run(c.caves.leave, db, user, cave_id)
    return {"ok": True}


@router.post("/caves/{cave_id}/invite")
def invite(cave_id: int, user: User = Depends(current_user), db: Session = Depends(get_db),
           c=Depends(get_container)):
    return {"recipients": _run(c.caves.invite, db, user, cave_id)}


class PatIn(BaseModel):
    line_id: int


@router.post("/caves/{cave_id}/pats")
def send_pat(cave_id: int, body: PatIn, user: User = Depends(current_user), db: Session = Depends(get_db),
             c=Depends(get_container)):
    _run(c.caves.send_pat, db, user, cave_id, body.line_id)
    return _run(c.caves.view, db, user, cave_id)


# ---- Telemetry ----------------------------------------------------------------------------


class EventIn(BaseModel):
    type: str
    ts: datetime | None = None
    props: dict = Field(default_factory=dict)


class EventsIn(BaseModel):
    events: list[EventIn] = Field(max_length=100)


@router.post("/telemetry")
def telemetry(body: EventsIn, user: User = Depends(current_user), db: Session = Depends(get_db),
              c=Depends(get_container)):
    accepted = 0
    for e in body.events:
        if e.type not in CLIENT_EVENTS:
            continue
        ts = e.ts.astimezone(timezone.utc).replace(tzinfo=None) if e.ts and e.ts.tzinfo else (e.ts or c.clock.now())
        db.add(TelemetryEvent(user_id=user.id, type=e.type, ts=ts, props=json.dumps(e.props, ensure_ascii=False)))
        accepted += 1
    db.commit()
    return {"accepted": accepted}


# ---- Người trực ---------------------------------------------------------------------------

admin = APIRouter(prefix="/v1/admin", dependencies=[Depends(require_admin)])


class NewFixture(BaseModel):
    opponent: str
    competition: str = ""
    home: bool = True
    kickoff: datetime


@admin.post("/fixtures")
def admin_create_fixture(body: NewFixture, db: Session = Depends(get_db), c=Depends(get_container)):
    kickoff = body.kickoff
    if kickoff.tzinfo is not None:
        kickoff = kickoff.astimezone(timezone.utc).replace(tzinfo=None)
    fixture = Fixture(provider="manual", provider_id=secrets.token_hex(6), kickoff=kickoff, opponent=body.opponent,
                      competition=body.competition, home=body.home, status="SCHEDULED")
    db.add(fixture)
    db.commit()
    return _fixture_json(fixture)


class ResultIn(BaseModel):
    mu_goals: int = Field(ge=0)
    opp_goals: int = Field(ge=0)
    mu_won_shootout: bool | None = None


@admin.post("/fixtures/{fixture_id}/result")
def admin_result(fixture_id: int, body: ResultIn, db: Session = Depends(get_db), c=Depends(get_container)):
    fixture = db.get(Fixture, fixture_id)
    if fixture is None:
        raise HTTPException(404, "fixture_not_found")
    try:
        c.matches.set_result(db, fixture, body.mu_goals, body.opp_goals, body.mu_won_shootout)
    except ValueError as e:
        raise HTTPException(409, str(e))
    return _fixture_json(fixture)


class ReplayIn(BaseModel):
    scenario: str = "atletico-2026-10-13"
    speed: float = Field(default=30, ge=1, le=600)


@admin.post("/replay")
def admin_replay(body: ReplayIn, db: Session = Depends(get_db), c=Depends(get_container)):
    try:
        fixture = c.matches.start_replay(db, body.scenario, body.speed)
    except KeyError:
        raise HTTPException(404, "unknown_scenario")
    return {**_fixture_json(fixture), "ends_at": fixture.replay_ends_at.isoformat() + "Z"}


class DemoIn(BaseModel):
    is_demo: bool


@admin.post("/groups/{group_id}/demo")
def admin_group_demo(group_id: int, body: DemoIn, db: Session = Depends(get_db)):
    group = db.get(Group, group_id)
    if group is None:
        raise HTTPException(404, "group_not_found")
    group.is_demo = body.is_demo
    db.commit()
    return {"id": group.id, "is_demo": group.is_demo}


@admin.post("/tick")
def admin_tick(db: Session = Depends(get_db), c=Depends(get_container)):
    return {"ok": True, "ran": c.matches.tick(db)}


@admin.post("/schedule/refresh")
def admin_refresh(db: Session = Depends(get_db), c=Depends(get_container)):
    c.matches.refresh_schedule(db, force=True)
    return {"ok": True}


@admin.get("/status")
def admin_status(db: Session = Depends(get_db), c=Depends(get_container)):
    error = db.get(KeyValue, "last_provider_error")
    refreshed = db.get(KeyValue, "schedule_refreshed_at")
    upcoming = db.scalars(
        select(Fixture).where(Fixture.processed_at.is_(None)).order_by(Fixture.kickoff).limit(10)
    )
    return {
        "provider": c.provider.name,
        "budget": {"used_today": c.budget.used_today(), "limit": c.budget.daily_limit} if c.budget else None,
        "schedule_refreshed_at": refreshed.value if refreshed else None,
        "last_provider_error": json.loads(error.value) if error else None,
        "pending_fixtures": [_fixture_json(f) for f in upcoming],
        "open_caves": db.scalar(select(func.count(Cave.id)).where(Cave.status == "OPEN")),
        "ai_pats": c.generator is not None,
    }


@admin.get("/outbox")
def admin_outbox(user_id: int | None = None, c=Depends(get_container)):
    if not isinstance(c.notifier, OutboxNotifier):
        raise HTTPException(404, "outbox_disabled")
    return [{"user_id": uid, "data": data} for uid, data in c.notifier.sent if user_id in (None, uid)]
