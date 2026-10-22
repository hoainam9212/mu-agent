from datetime import datetime

import httpx
import pytest

from vaohang.clock import FakeClock
from vaohang.models import make_sessionmaker
from vaohang.providers import BudgetExceeded, ProviderError
from vaohang.providers.api_football import ApiFootballProvider, parse_fixture, season_for
from vaohang.providers.budget import RequestBudget
from vaohang.providers.football_data import parse_match

# Mẫu theo tài liệu công khai của từng nhà cung cấp (chưa đối chiếu với key thật).
AF_LOSS_AWAY = {
    "fixture": {"id": 1001, "date": "2026-10-13T19:00:00+00:00", "status": {"short": "FT", "elapsed": 90}},
    "league": {"id": 2, "name": "UEFA Champions League"},
    "teams": {"home": {"id": 530, "name": "Atletico Madrid", "winner": True},
              "away": {"id": 33, "name": "Manchester United", "winner": False}},
    "goals": {"home": 2, "away": 0},
    "score": {"penalty": {"home": None, "away": None}},
}
AF_PEN_WIN = {
    "fixture": {"id": 1002, "date": "2026-11-01T15:00:00Z", "status": {"short": "PEN", "elapsed": 120}},
    "league": {"id": 45, "name": "FA Cup"},
    "teams": {"home": {"id": 33, "name": "Manchester United", "winner": True},
              "away": {"id": 40, "name": "Liverpool", "winner": False}},
    "goals": {"home": 1, "away": 1},
    "score": {"penalty": {"home": 5, "away": 4}},
}


def test_api_football_parse_loss_and_shootout():
    loss = parse_fixture(AF_LOSS_AWAY, 33)
    assert (loss.home, loss.opponent, loss.mu_goals, loss.opp_goals, loss.result) == (
        False, "Atletico Madrid", 0, 2, "L")
    assert loss.kickoff == datetime(2026, 10, 13, 19, 0)
    win = parse_fixture(AF_PEN_WIN, 33)
    assert (win.status, win.result, win.mu_goals) == ("FINISHED", "W", 1)


def test_api_football_live_has_no_result():
    live = {**AF_LOSS_AWAY, "fixture": {**AF_LOSS_AWAY["fixture"], "status": {"short": "2H"}}}
    info = parse_fixture(live, 33)
    assert info.status == "LIVE" and info.result is None


def test_api_football_friendly_flag():
    friendly = {**AF_LOSS_AWAY, "league": {"id": 667, "name": "Friendlies Clubs"}}
    assert parse_fixture(friendly, 33).friendly


def test_season_for():
    assert season_for(datetime(2026, 10, 1)) == 2026
    assert season_for(datetime(2027, 3, 1)) == 2026


def test_football_data_parse():
    match = {
        "id": 555, "utcDate": "2026-11-03T20:00:00Z", "status": "FINISHED",
        "competition": {"name": "UEFA Champions League"},
        "homeTeam": {"id": 66, "name": "Manchester United FC", "shortName": "Man United"},
        "awayTeam": {"id": 100, "name": "AS Roma", "shortName": "Roma"},
        "score": {"winner": "AWAY_TEAM", "duration": "REGULAR", "fullTime": {"home": 1, "away": 2}},
    }
    info = parse_match(match, 66)
    assert (info.home, info.opponent, info.mu_goals, info.opp_goals, info.result) == (True, "Roma", 1, 2, "L")


def test_football_data_shootout_uses_regular_and_extra_time():
    match = {
        "id": 556, "utcDate": "2026-11-03T20:00:00Z", "status": "FINISHED", "competition": {"name": "Cup"},
        "homeTeam": {"id": 100, "name": "Roma"}, "awayTeam": {"id": 66, "name": "Man United"},
        "score": {"winner": "AWAY_TEAM", "duration": "PENALTY_SHOOTOUT", "fullTime": {"home": 5, "away": 6},
                  "regularTime": {"home": 1, "away": 1}, "extraTime": {"home": 0, "away": 0},
                  "penalties": {"home": 4, "away": 5}},
    }
    info = parse_match(match, 66)
    assert (info.result, info.mu_goals, info.opp_goals) == ("W", 1, 1)


def test_budget_blocks_calls_after_limit():
    sessions = make_sessionmaker("sqlite://")
    clock = FakeClock(datetime(2026, 11, 3, 10, 0))
    budget = RequestBudget("api_football", 2, sessions, clock)
    calls = []
    budget.spend(lambda: calls.append(1))
    budget.spend(lambda: calls.append(1))
    with pytest.raises(BudgetExceeded):
        budget.spend(lambda: calls.append(1))
    assert len(calls) == 2
    clock.advance(days=1)  # sang ngày UTC mới thì có lại hạn mức
    budget.spend(lambda: calls.append(1))
    assert budget.used_today() == 1


def test_api_football_reports_plan_errors():
    def handler(request):
        return httpx.Response(200, json={"errors": {"plan": "Free plans do not have access to this season"},
                                         "response": []})

    sessions = make_sessionmaker("sqlite://")
    budget = RequestBudget("api_football", 90, sessions, FakeClock(datetime(2026, 11, 3)))
    http = httpx.Client(base_url="https://example.test", transport=httpx.MockTransport(handler))
    provider = ApiFootballProvider("key", 33, budget, http=http)
    with pytest.raises(ProviderError, match="Free plans"):
        provider.schedule(datetime(2026, 11, 3))
