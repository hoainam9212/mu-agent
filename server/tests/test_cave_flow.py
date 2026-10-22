"""Cả vòng lặp qua HTTP API: thua → vào hang (tự chọn) → vỗ vai → tắt đèn → hoà → thắng → ra hang."""

import json

import pytest

from .conftest import ADMIN, Api


def kinds(container, user):
    return [m["kind"] for m in container.notifier.for_user(user.id)]


def last(container, user, kind):
    return [m for m in container.notifier.for_user(user.id) if m["kind"] == kind][-1]


def result(client, mu, opp, opponent="Roma"):
    f = client.post("/v1/admin/fixtures", headers=ADMIN,
                    json={"opponent": opponent, "kickoff": "2026-11-03T20:00:00Z"}).json()
    r = client.post(f"/v1/admin/fixtures/{f['id']}/result", headers=ADMIN, json={"mu_goals": mu, "opp_goals": opp})
    assert r.status_code == 200, r.text
    return f["id"]


@pytest.fixture
def crew(client):
    a, b, c, d = (Api(client, n) for n in ("Bình", "Tuấn", "Hùng", "Nam"))
    g = a.post("/v1/groups", json={"name": "Hội MU Cầu Giấy"}).json()
    for u in (b, c, d):
        assert u.post("/v1/groups/join", json={"invite_code": g["invite_code"]}).json()["status"] == "pending"
        assert a.post(f"/v1/groups/{g['id']}/members/{u.id}/approve").status_code == 200
    return a, b, c, d


def cave_id(user):
    return user.get("/v1/groups").json()[0]["cave"]["id"]


def test_join_requires_owner_approval(client):
    a, b = Api(client, "A"), Api(client, "B")
    g = a.post("/v1/groups", json={"name": "Hội"}).json()
    b.post("/v1/groups/join", json={"invite_code": g["invite_code"]})
    assert b.get("/v1/groups").json()[0]["status"] == "pending"
    assert b.post(f"/v1/groups/{g['id']}/members/{b.id}/approve").status_code == 403
    pending = a.get("/v1/groups").json()[0]["pending"]
    assert [p["nickname"] for p in pending] == ["B"]


def test_full_loop(client, container, clock, crew):
    a, b, c, d = crew

    # 1. MU thua → hang mở, mọi người được báo, chưa ai trong hang
    fx1 = result(client, 0, 2)
    cid = cave_id(a)
    for u in crew:
        msg = last(container, u, "cave_lights_on")
        assert msg["you_inside"] == "0" and msg["inside_names"] == "[]" and msg["result"] == "L"

    # 2. Vào hang là tự chọn: chỉ Bình vào
    view = a.post(f"/v1/caves/{cid}/enter", json={"source": "notification"}).json()
    assert view["me_inside"] and view["lights"]["on"] and len(view["choices"]) == 4

    # 3. Rủ anh em: chỉ người đang thức, ở ngoài hang
    b.post("/v1/presence", json={"fixture_id": fx1, "state": "awake"})
    c.post("/v1/presence", json={"fixture_id": fx1, "state": "asleep"})
    assert a.post(f"/v1/caves/{cid}/invite").json() == {"recipients": 1}
    assert "cave_invite" in kinds(container, b) and "cave_invite" not in kinds(container, c)
    assert a.post(f"/v1/caves/{cid}/invite").status_code == 409  # 1 lời mời mỗi trận

    # 4. Vỗ vai: chỉ người trong hang nhận
    b.post(f"/v1/caves/{cid}/enter", json={"source": "invite"})
    line = a.get(f"/v1/caves/{cid}").json()["choices"][0]
    view = a.post(f"/v1/caves/{cid}/pats", json={"line_id": line["id"]}).json()
    assert view["pats"][0]["from"] == "Bình"
    assert last(container, b, "cave_pat")["text"] == line["text"]
    assert "cave_pat" not in kinds(container, c)

    # 5. Người ở ngoài chỉ thấy ai đang trong hang, không thấy vỗ vai
    outside = c.get(f"/v1/caves/{cid}").json()
    assert [u["nickname"] for u in outside["inside"]] == ["Bình", "Tuấn"]
    assert outside["pats"] == [] and outside["choices"] == []
    assert outside["me_inside"] is False

    # 6. Tắt đèn sau 30 phút: hang vẫn mở, không vỗ vai được nữa
    clock.advance(minutes=31)
    assert a.post(f"/v1/caves/{cid}/pats", json={"line_id": line["id"]}).status_code == 409
    view = a.get(f"/v1/caves/{cid}").json()
    assert view["status"] == "OPEN" and view["lights"]["on"] is False

    # 7. Ra hang lặng lẽ, vào lại được
    before = len(container.notifier.sent)
    a.post(f"/v1/caves/{cid}/leave")
    assert len(container.notifier.sent) == before
    assert a.get(f"/v1/caves/{cid}").json()["me_inside"] is False
    a.post(f"/v1/caves/{cid}/enter")

    # 8. Hoà → cùng hang, sáng đèn lại
    clock.advance(days=3)
    result(client, 1, 1)
    assert cave_id(a) == cid
    assert last(container, a, "cave_lights_on")["you_inside"] == "1"
    assert last(container, c, "cave_lights_on")["you_inside"] == "0"
    assert sorted(json.loads(last(container, c, "cave_lights_on")["inside_names"])) == ["Bình", "Tuấn"]

    # 9. Thắng → hang đóng, chỉ người trong hang được gọi ra
    clock.advance(days=4)
    result(client, 2, 1)
    assert "cave_exit" in kinds(container, a) and "cave_exit" in kinds(container, b)
    assert "cave_exit" not in kinds(container, c) and "cave_exit" not in kinds(container, d)
    assert a.get("/v1/groups").json()[0]["cave"] is None
    assert a.post(f"/v1/caves/{cid}/enter").status_code == 409

    # 10. Thua tiếp → hang mới
    clock.advance(days=3)
    result(client, 0, 1)
    assert cave_id(a) != cid


def test_draw_when_cave_closed_does_nothing(client, container, crew):
    a = crew[0]
    result(client, 2, 2)
    assert a.get("/v1/groups").json()[0]["cave"] is None
    assert "cave_lights_on" not in kinds(container, a)


def test_outsiders_cannot_use_cave(client, crew):
    a = crew[0]
    result(client, 0, 1)
    cid = cave_id(a)
    stranger = Api(client, "Lạ")
    assert stranger.get(f"/v1/caves/{cid}").status_code == 403
    assert stranger.post(f"/v1/caves/{cid}/enter").status_code == 403


def test_pat_limit_per_window(client, container, crew):
    a = crew[0]
    result(client, 0, 1)
    cid = cave_id(a)
    a.post(f"/v1/caves/{cid}/enter")
    line = a.get(f"/v1/caves/{cid}").json()["choices"][0]["id"]
    for _ in range(container.settings.max_pats_per_window):
        assert a.post(f"/v1/caves/{cid}/pats", json={"line_id": line}).status_code == 200
    assert a.post(f"/v1/caves/{cid}/pats", json={"line_id": line}).status_code == 429


def test_replay_only_touches_demo_groups(client, container, clock, crew):
    a = crew[0]
    demo_owner = Api(client, "Demo")
    demo = demo_owner.post("/v1/groups", json={"name": "Demo"}).json()
    client.post(f"/v1/admin/groups/{demo['id']}/demo", headers=ADMIN, json={"is_demo": True})

    r = client.post("/v1/admin/replay", headers=ADMIN, json={"scenario": "atletico-2026-10-13", "speed": 30})
    assert r.status_code == 200
    client.post("/v1/admin/tick", headers=ADMIN)
    assert demo_owner.get("/v1/groups").json()[0]["cave"] is None  # chưa hết trận
    clock.advance(minutes=4)  # 95 phút / 30 ≈ 3,2 phút
    client.post("/v1/admin/tick", headers=ADMIN)
    assert demo_owner.get("/v1/groups").json()[0]["cave"]["status"] == "OPEN"
    assert last(container, demo_owner, "cave_lights_on")["score"] == "Atlético Madrid 2-0 MU"
    assert a.get("/v1/groups").json()[0]["cave"] is None


def test_admin_requires_token(client):
    assert client.post("/v1/admin/replay", json={}).status_code == 403
    assert client.get("/v1/admin/status", headers={"X-Admin-Token": "wrong"}).status_code == 403


def test_telemetry_accepts_known_events_only(client):
    u = Api(client, "T")
    r = u.post("/v1/telemetry", json={"events": [
        {"type": "screen_off_after_ft", "props": {"minutes": 12}},
        {"type": "keystrokes", "props": {}},
    ]})
    assert r.json() == {"accepted": 1}
