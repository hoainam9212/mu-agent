"""Cấu hình đọc từ biến môi trường."""

from __future__ import annotations

import os
from dataclasses import dataclass


def _env(name: str, default: str | None = None) -> str | None:
    value = os.environ.get(name)
    return value if value not in (None, "") else default


def _int(name: str, default: int) -> int:
    return int(_env(name, str(default)))


def _bool(name: str, default: bool) -> bool:
    return _env(name, "1" if default else "0").lower() in ("1", "true", "yes", "on")


@dataclass
class Settings:
    database_url: str = "sqlite:///./vaohang.db"

    # Nguồn dữ liệu trận: "football_data" | "api_football" | "manual"
    match_provider: str = "manual"
    api_football_key: str | None = None
    api_football_team_id: int = 33
    # Gói Free của API-Football: 100 request/ngày (reset 00:00 UTC). Chừa biên an toàn.
    api_football_daily_budget: int = 90
    football_data_token: str | None = None
    football_data_team_id: int = 66
    football_data_daily_budget: int = 1000

    # Chỉ cần kết quả cuối trận, không cần diễn biến → bắt đầu hỏi từ phút 105 sau giờ bóng lăn,
    # mỗi 3 phút một lần, bỏ cuộc sau 6 tiếng. Mỗi trận tốn khoảng 5–40 request.
    result_poll_start_min: int = 105
    result_poll_interval_s: int = 180
    result_poll_giveup_h: int = 6
    schedule_refresh_h: int = 12

    light_minutes: int = 30
    reminder_minutes: int = 30
    max_pats_per_window: int = 5

    admin_token: str | None = None
    fcm_enabled: bool = False

    pat_model: str = "claude-opus-5-5"
    pats_ai_enabled: bool = False

    poller_enabled: bool = True
    poll_tick_s: int = 60

    @classmethod
    def from_env(cls) -> "Settings":
        football_data_token = _env("FOOTBALL_DATA_TOKEN")
        api_football_key = _env("API_FOOTBALL_KEY")
        if football_data_token:
            default_provider = "football_data"
        elif api_football_key:
            default_provider = "api_football"
        else:
            default_provider = "manual"
        return cls(
            database_url=_env("DATABASE_URL", cls.database_url),
            match_provider=_env("MATCH_PROVIDER", default_provider),
            api_football_key=api_football_key,
            api_football_team_id=_int("API_FOOTBALL_TEAM_ID", 33),
            api_football_daily_budget=_int("API_FOOTBALL_DAILY_BUDGET", 90),
            football_data_token=football_data_token,
            football_data_team_id=_int("FOOTBALL_DATA_TEAM_ID", 66),
            football_data_daily_budget=_int("FOOTBALL_DATA_DAILY_BUDGET", 1000),
            result_poll_start_min=_int("RESULT_POLL_START_MIN", 105),
            result_poll_interval_s=_int("RESULT_POLL_INTERVAL_S", 180),
            result_poll_giveup_h=_int("RESULT_POLL_GIVEUP_H", 6),
            schedule_refresh_h=_int("SCHEDULE_REFRESH_H", 12),
            light_minutes=_int("LIGHT_MINUTES", 30),
            reminder_minutes=_int("REMINDER_MINUTES", 30),
            max_pats_per_window=_int("MAX_PATS_PER_WINDOW", 5),
            admin_token=_env("ADMIN_TOKEN"),
            fcm_enabled=_bool("FCM_ENABLED", False),
            pat_model=_env("PAT_MODEL", "claude-opus-5-5"),
            pats_ai_enabled=_bool("PATS_AI_ENABLED", bool(_env("ANTHROPIC_API_KEY"))),
            poller_enabled=_bool("POLLER_ENABLED", True),
            poll_tick_s=_int("POLL_TICK_S", 60),
        )
