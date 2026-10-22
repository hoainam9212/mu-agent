"""football-data.org v4.

Gói Free có Premier League và Champions League mùa hiện tại (không có FA Cup, League Cup,
Europa League), giới hạn 10 request/phút. Chưa thử với token thật – cần kiểm tra khi có token.
"""

from __future__ import annotations

from datetime import datetime, timedelta

import httpx

from ..rules import DRAW, LOSS, WIN
from . import FixtureInfo, parse_iso_utc
from .budget import RequestBudget

BASE_URL = "https://api.football-data.org/v4"

_STATUS = {
    **dict.fromkeys(["SCHEDULED", "TIMED"], "SCHEDULED"),
    **dict.fromkeys(["IN_PLAY", "PAUSED", "LIVE", "EXTRA_TIME", "PENALTY_SHOOTOUT"], "LIVE"),
    **dict.fromkeys(["FINISHED", "AWARDED"], "FINISHED"),
    **dict.fromkeys(["POSTPONED", "SUSPENDED"], "POSTPONED"),
    "CANCELLED": "CANCELLED",
}


def _goals(score: dict, side: str) -> int | None:
    # Với trận đá luân lưu, fullTime có thể đã cộng bàn luân lưu → ưu tiên regularTime + extraTime.
    if score.get("duration") == "PENALTY_SHOOTOUT" and score.get("regularTime"):
        regular = score["regularTime"].get(side) or 0
        extra = (score.get("extraTime") or {}).get(side) or 0
        return regular + extra
    return (score.get("fullTime") or {}).get(side)


def parse_match(match: dict, team_id: int) -> FixtureInfo:
    home = match["homeTeam"]["id"] == team_id
    mu_side, opp_side = ("home", "away") if home else ("away", "home")
    opp_team = match["awayTeam"] if home else match["homeTeam"]
    status = _STATUS.get(match["status"], "SCHEDULED")
    score = match.get("score") or {}
    result = None
    if status == "FINISHED":
        winner = score.get("winner")
        if winner == "DRAW":
            result = DRAW
        elif winner in ("HOME_TEAM", "AWAY_TEAM"):
            result = WIN if (winner == "HOME_TEAM") == home else LOSS
    competition = (match.get("competition") or {}).get("name", "")
    return FixtureInfo(
        provider_id=str(match["id"]),
        kickoff=parse_iso_utc(match["utcDate"]),
        opponent=opp_team.get("shortName") or opp_team.get("name", ""),
        competition=competition,
        home=home,
        status=status,
        mu_goals=_goals(score, mu_side),
        opp_goals=_goals(score, opp_side),
        result=result,
        friendly="friendl" in competition.lower(),
    )


class FootballDataProvider:
    name = "football_data"

    def __init__(self, token: str, team_id: int, budget: RequestBudget, http: httpx.Client | None = None):
        self.team_id = team_id
        self.budget = budget
        self.http = http or httpx.Client(base_url=BASE_URL, headers={"X-Auth-Token": token}, timeout=20)

    def _get(self, path: str, params: dict | None = None) -> dict:
        response = self.budget.spend(lambda: self.http.get(path, params=params))
        response.raise_for_status()
        return response.json()

    def schedule(self, now: datetime) -> list[FixtureInfo]:
        body = self._get(
            f"/teams/{self.team_id}/matches",
            {
                "dateFrom": (now - timedelta(days=3)).date().isoformat(),
                "dateTo": (now + timedelta(days=7)).date().isoformat(),
            },
        )
        return [parse_match(m, self.team_id) for m in body.get("matches", [])]

    def fixture(self, provider_id: str) -> FixtureInfo | None:
        return parse_match(self._get(f"/matches/{provider_id}"), self.team_id)
