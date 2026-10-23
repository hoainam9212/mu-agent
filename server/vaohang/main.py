"""Khởi tạo app: `uvicorn --factory vaohang.main:create_app`."""

from __future__ import annotations

import logging
import threading
from contextlib import asynccontextmanager
from dataclasses import dataclass

from fastapi import FastAPI
from sqlalchemy.orm import sessionmaker

from .api.routes import admin, router
from .clock import Clock
from .config import Settings
from .models import make_sessionmaker
from .notifier import FcmNotifier, Notifier, OutboxNotifier
from .pats import LineGenerator
from .providers import ManualProvider, MatchProvider
from .providers.budget import RequestBudget
from .services.cave import CaveService
from .services.matches import MatchService

log = logging.getLogger(__name__)


@dataclass
class Container:
    settings: Settings
    clock: Clock
    sessions: sessionmaker
    notifier: Notifier
    provider: MatchProvider
    budget: RequestBudget | None
    generator: LineGenerator | None
    caves: CaveService
    matches: MatchService


def build_provider(settings: Settings, sessions: sessionmaker, clock: Clock):
    if settings.match_provider == "football_data" and settings.football_data_token:
        from .providers.football_data import FootballDataProvider

        budget = RequestBudget("football_data", settings.football_data_daily_budget, sessions, clock)
        return FootballDataProvider(settings.football_data_token, settings.football_data_team_id, budget), budget
    if settings.match_provider == "api_football" and settings.api_football_key:
        from .providers.api_football import ApiFootballProvider

        budget = RequestBudget("api_football", settings.api_football_daily_budget, sessions, clock)
        return ApiFootballProvider(settings.api_football_key, settings.api_football_team_id, budget), budget
    return ManualProvider(), None


def build_container(
    settings: Settings | None = None,
    clock: Clock | None = None,
    notifier: Notifier | None = None,
    provider: MatchProvider | None = None,
    generator: LineGenerator | None = None,
) -> Container:
    settings = settings or Settings.from_env()
    clock = clock or Clock()
    sessions = make_sessionmaker(settings.database_url)
    if notifier is None:
        notifier = FcmNotifier() if settings.fcm_enabled else OutboxNotifier()
    budget = None
    if provider is None:
        provider, budget = build_provider(settings, sessions, clock)
    if generator is None and settings.pats_ai_enabled:
        from .pats.claude import ClaudeLineGenerator

        generator = ClaudeLineGenerator(settings.pat_model)
    caves = CaveService(settings, clock, notifier, generator)
    matches = MatchService(settings, clock, provider, caves, notifier)
    return Container(settings, clock, sessions, notifier, provider, budget, generator, caves, matches)


def _poll_loop(container: Container, stop: threading.Event) -> None:
    while not stop.is_set():
        try:
            with container.sessions() as db:
                container.matches.tick(db)
        except Exception:  # vòng canh trận không được chết giữa đêm
            log.exception("poller tick failed")
        stop.wait(container.settings.poll_tick_s)


def create_app(container: Container | None = None) -> FastAPI:
    if container is None:
        logging.basicConfig(level=logging.INFO)
        container = build_container()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        stop = threading.Event()
        thread = None
        if container.settings.poller_enabled:
            thread = threading.Thread(target=_poll_loop, args=(container, stop), daemon=True, name="poller")
            thread.start()
        yield
        stop.set()
        if thread:
            thread.join(timeout=5)

    app = FastAPI(title="Vào Hang", lifespan=lifespan)
    app.state.container = container
    app.include_router(router)
    app.include_router(admin)

    # Cloud Run giữ riêng một số đường dẫn tận cùng bằng "z" (như /healthz) → dùng /health.
    @app.get("/health")
    @app.get("/healthz")
    def healthz():
        return {"ok": True, "provider": container.provider.name}

    return app
