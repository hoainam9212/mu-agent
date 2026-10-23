"""Canh trận với ít request: lịch 12 tiếng/lần, kết quả từ phút 105, mỗi 3 phút."""

from datetime import datetime, timedelta

from sqlalchemy import select

from vaohang.models import Fixture
from vaohang.providers import FixtureInfo

from .conftest import START, Api


def info(pid="r1", kickoff=START + timedelta(hours=2), status="SCHEDULED", mu=None, opp=None, result=None,
         friendly=False):
    return FixtureInfo(provider_id=pid, kickoff=kickoff, opponent="Roma", competition="UEFA Champions League",
                       home=True, status=status, mu_goals=mu, opp_goals=opp, result=result, friendly=friendly)


def tick(container):
    with container.sessions() as db:
        container.matches.tick(db)


def fixture(container, pid="r1"):
    with container.sessions() as db:
        return db.scalar(select(Fixture).where(Fixture.provider_id == pid))


def test_schedule_refreshed_twice_a_day(container, provider, clock):
    provider.fixtures["r1"] = info()
    tick(container)
    tick(container)
    assert provider.schedule_calls == 1
    clock.advance(hours=12, minutes=1)
    tick(container)
    assert provider.schedule_calls == 2


def test_result_polling_starts_after_105_minutes_and_respects_interval(container, provider, clock, client):
    a = Api(client, "A")
    a.post("/v1/groups", json={"name": "Hội"})
    kickoff = START + timedelta(minutes=10)
    provider.fixtures["r1"] = info(kickoff=kickoff)
    tick(container)
    clock.set(kickoff + timedelta(minutes=100))
    tick(container)
    assert provider.fixture_calls == 0

    clock.set(kickoff + timedelta(minutes=106))
    tick(container)
    assert provider.fixture_calls == 1
    clock.advance(minutes=1)
    tick(container)
    assert provider.fixture_calls == 1  # chưa đủ 3 phút

    provider.fixtures["r1"] = info(kickoff=kickoff, status="FINISHED", mu=0, opp=2, result="L")
    clock.advance(minutes=3)
    tick(container)
    assert provider.fixture_calls == 2
    assert fixture(container).processed_at is not None
    assert a.get("/v1/groups").json()[0]["cave"]["status"] == "OPEN"

    clock.advance(minutes=3)
    tick(container)
    assert provider.fixture_calls == 2  # đã xong, không hỏi nữa


def test_old_results_seen_at_startup_do_not_open_caves(container, provider, client):
    a = Api(client, "A")
    a.post("/v1/groups", json={"name": "Hội"})
    provider.fixtures["old"] = info(pid="old", kickoff=START - timedelta(days=2), status="FINISHED", mu=0, opp=3,
                                    result="L")
    tick(container)
    assert fixture(container, "old").processed_at is not None
    assert a.get("/v1/groups").json()[0]["cave"] is None
    assert container.notifier.for_user(a.id) == []


def test_friendlies_never_touch_caves(container, provider, clock, client):
    a = Api(client, "A")
    a.post("/v1/groups", json={"name": "Hội"})
    kickoff = START - timedelta(minutes=110)
    provider.fixtures["f1"] = info(pid="f1", kickoff=kickoff, status="FINISHED", mu=0, opp=4, result="L",
                                   friendly=True)
    tick(container)
    assert fixture(container, "f1").result is None
    assert a.get("/v1/groups").json()[0]["cave"] is None


def test_reminder_sent_once_30_minutes_before(container, provider, clock, client):
    a = Api(client, "A")
    provider.fixtures["r1"] = info(kickoff=START + timedelta(minutes=45))
    tick(container)
    assert container.notifier.for_user(a.id) == []
    clock.advance(minutes=20)
    tick(container)
    tick(container)
    reminders = [m for m in container.notifier.for_user(a.id) if m["kind"] == "match_reminder"]
    assert len(reminders) == 1 and reminders[0]["opponent"] == "Roma"


def test_provider_error_is_recorded_not_raised(container, provider, client):
    from vaohang.providers import ProviderError

    def broken(now):
        raise ProviderError("API-Football: Free plans do not have access to this season")

    provider.schedule = broken
    tick(container)
    status = client.get("/v1/admin/status", headers={"X-Admin-Token": "secret"}).json()
    assert "Free plans" in status["last_provider_error"]["error"]


def test_overlapping_tick_is_skipped(container, provider):
    provider.fixtures["r1"] = info()
    container.matches._tick_lock.acquire()  # giả lập vòng trước đang chạy
    try:
        with container.sessions() as db:
            assert container.matches.tick(db) is False
        assert fixture(container) is None
    finally:
        container.matches._tick_lock.release()
    with container.sessions() as db:
        assert container.matches.tick(db) is True
    assert fixture(container) is not None


def test_database_url_from_supabase_uses_psycopg():
    from vaohang.models import normalize_database_url

    assert normalize_database_url("postgresql://u:p@h:5432/db") == "postgresql+psycopg://u:p@h:5432/db"
    assert normalize_database_url("postgres://u:p@h/db") == "postgresql+psycopg://u:p@h/db"
    assert normalize_database_url("postgresql+psycopg://x") == "postgresql+psycopg://x"
    assert normalize_database_url("sqlite:///./v.db") == "sqlite:///./v.db"
