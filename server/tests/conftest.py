from __future__ import annotations

from datetime import datetime

import pytest
from fastapi.testclient import TestClient

from vaohang.clock import FakeClock
from vaohang.config import Settings
from vaohang.main import build_container, create_app
from vaohang.notifier import OutboxNotifier
from vaohang.providers import FixtureInfo

START = datetime(2026, 11, 3, 18, 0)  # 01:00 ngày 4/11 giờ VN
ADMIN = {"X-Admin-Token": "secret"}


class FakeProvider:
    name = "api_football"

    def __init__(self):
        self.fixtures: dict[str, FixtureInfo] = {}
        self.schedule_calls = 0
        self.fixture_calls = 0

    def schedule(self, now):
        self.schedule_calls += 1
        return list(self.fixtures.values())

    def fixture(self, provider_id):
        self.fixture_calls += 1
        return self.fixtures.get(provider_id)


def make_settings(**overrides) -> Settings:
    base = dict(database_url="sqlite://", poller_enabled=False, admin_token="secret", match_provider="manual")
    base.update(overrides)
    return Settings(**base)


@pytest.fixture
def clock():
    return FakeClock(START)


@pytest.fixture
def provider():
    return FakeProvider()


@pytest.fixture
def container(clock, provider):
    return build_container(make_settings(), clock=clock, notifier=OutboxNotifier(), provider=provider)


@pytest.fixture
def client(container):
    with TestClient(create_app(container)) as c:
        yield c


class Api:
    """Gọi API dưới danh nghĩa một người dùng."""

    def __init__(self, client: TestClient, nickname: str):
        self.client = client
        body = client.post("/v1/users", json={"nickname": nickname, "fcm_token": f"tok-{nickname}"}).json()
        self.id = body["user_id"]
        self.headers = {"Authorization": f"Bearer {body['token']}"}

    def get(self, path, **kw):
        return self.client.get(path, headers=self.headers, **kw)

    def post(self, path, **kw):
        return self.client.post(path, headers=self.headers, **kw)

    def delete(self, path, **kw):
        return self.client.delete(path, headers=self.headers, **kw)
