"""API-Football (api-sports.io) v3.

Lưu ý gói Free: 100 request/ngày; theo nhiều báo cáo, gói Free có thể bị giới hạn mùa giải
(lỗi kiểu "Free plans do not have access to this season"). Lỗi trong trường `errors` được
đưa ra nguyên văn để người trực thấy ngay. Chưa thử với key thật – cần kiểm tra khi có key.
"""

from __future__ import annotations

from datetime import datetime, timedelta

import httpx

from ..rules import mu_result
from . import FixtureInfo, ProviderError, parse_iso_utc
from .budget import RequestBudget

BASE_URL = "https://v3.football.api-sports.io"

_STATUS = {
    **dict.fromkeys(["TBD", "NS"], "SCHEDULED"),
    **dict.fromkeys(["1H", "HT", "2H", "ET", "BT", "P", "LIVE", "SUSP", "INT"], "LIVE"),
    **dict.fromkeys(["FT", "AET", "PEN", "AWD", "WO"], "FINISHED"),
    "PST": "POSTPONED",
    **dict.fromkeys(["CANC", "ABD"], "CANCELLED"),
}


def season_for(day: datetime) -> int:
    return day.year if day.month >= 7 else day.year - 1


def parse_fixture(item: dict, team_id: int) -> FixtureInfo:
    fx, teams, goals = item["fixture"], item["teams"], item.get("goals") or {}
    home = teams["home"]["id"] == team_id
    mu_side, opp_side = ("home", "away") if home else ("away", "home")
    short = fx["status"]["short"]
    status = _STATUS.get(short, "SCHEDULED")
    mu_goals, opp_goals = goals.get(mu_side), goals.get(opp_side)
    result = None
    if status == "FINISHED":
        mu_winner = teams[mu_side].get("winner")
        if short in ("PEN", "AWD", "WO") and mu_winner is not None:
            result = mu_result(mu_goals or 0, opp_goals or 0, mu_won_shootout=bool(mu_winner))
        elif mu_goals is not None and opp_goals is not None:
            result = mu_result(mu_goals, opp_goals)
    league = item.get("league") or {}
    return FixtureInfo(
        provider_id=str(fx["id"]),
        kickoff=parse_iso_utc(fx["date"]),
        opponent=teams[opp_side]["name"],
        competition=league.get("name", ""),
        home=home,
        status=status,
        mu_goals=mu_goals,
        opp_goals=opp_goals,
        result=result,
        friendly="friendl" in league.get("name", "").lower(),
    )


class ApiFootballProvider:
    name = "api_football"

    def __init__(self, key: str, team_id: int, budget: RequestBudget, http: httpx.Client | None = None):
        self.team_id = team_id
        self.budget = budget
        self.http = http or httpx.Client(base_url=BASE_URL, headers={"x-apisports-key": key}, timeout=20)

    def _get(self, params: dict) -> list[dict]:
        response = self.budget.spend(lambda: self.http.get("/fixtures", params=params))
        response.raise_for_status()
        body = response.json()
        if body.get("errors"):
            raise ProviderError(f"API-Football: {body['errors']}")
        return body.get("response", [])

    def schedule(self, now: datetime) -> list[FixtureInfo]:
        items = self._get(
            {
                "team": self.team_id,
                "season": season_for(now),
                "from": (now - timedelta(days=3)).date().isoformat(),
                "to": (now + timedelta(days=7)).date().isoformat(),
            }
        )
        return [parse_fixture(item, self.team_id) for item in items]

    def fixture(self, provider_id: str) -> FixtureInfo | None:
        items = self._get({"id": provider_id})
        return parse_fixture(items[0], self.team_id) if items else None
