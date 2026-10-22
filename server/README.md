# Máy chủ Vào Hang

FastAPI + SQLAlchemy. Canh kết quả trận MU, mở/giữ/đóng hang theo luật đã chốt, gửi FCM data message cho app,
soạn câu vỗ vai bằng Claude (có bộ lọc và kho dự phòng).

## Chạy thử trên máy

```bash
cd server
python3 -m venv .venv && . .venv/bin/activate
pip install -e ".[dev]"
pytest -q                                   # 71 test

ADMIN_TOKEN=dev uvicorn --factory vaohang.main:create_app --port 8000
```

Không có token/key nào thì máy chủ chạy ở chế độ `manual` (trận và kết quả nhập tay), thông báo không gửi thật
mà giữ trong outbox: `GET /v1/admin/outbox` (header `X-Admin-Token`).

## Biến môi trường

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DATABASE_URL` | `sqlite:///./vaohang.db` | Postgres: `postgresql+psycopg://…` (cài thêm `.[postgres]`) |
| `FOOTBALL_DATA_TOKEN` | – | Token miễn phí của football-data.org (có PL + Champions League) |
| `API_FOOTBALL_KEY` | – | Key API-Football (gói Free 100 request/ngày) |
| `MATCH_PROVIDER` | tự chọn theo key có sẵn | `football_data` \| `api_football` \| `manual` |
| `API_FOOTBALL_DAILY_BUDGET` | `90` | Trần request/ngày UTC, chừa biên dưới 100 của gói Free |
| `RESULT_POLL_START_MIN` / `RESULT_POLL_INTERVAL_S` | `105` / `180` | Hỏi kết quả từ phút 105 sau giờ bóng lăn, mỗi 3 phút |
| `LIGHT_MINUTES` | `30` | Thời gian sáng đèn sau mỗi trận không thắng |
| `ADMIN_TOKEN` | – | Bắt buộc để dùng `/v1/admin/*` |
| `FCM_ENABLED` | `0` | `1` để gửi thật qua Firebase (cài `.[fcm]`, đặt `GOOGLE_APPLICATION_CREDENTIALS`) |
| `ANTHROPIC_API_KEY` | – | Có key thì bật câu vỗ vai do AI soạn; không có thì dùng kho dự phòng |
| `PAT_MODEL` | `claude-opus-5-5` | Model soạn câu |
| `POLLER_ENABLED` / `POLL_TICK_S` | `1` / `60` | Vòng canh trận chạy nền |

## Việc của người trực (đêm pilot)

```bash
H='X-Admin-Token: dev'
curl -H "$H" localhost:8000/v1/admin/status                    # nguồn dữ liệu, số request hôm nay, lỗi gần nhất
curl -H "$H" -X POST localhost:8000/v1/admin/schedule/refresh  # lấy lịch ngay

# API lỗi lúc 4h sáng → nhập tay
curl -H "$H" -H 'content-type: application/json' -X POST localhost:8000/v1/admin/fixtures \
  -d '{"opponent":"Roma","competition":"UEFA Champions League","home":true,"kickoff":"2026-11-03T20:00:00Z"}'
curl -H "$H" -H 'content-type: application/json' -X POST localhost:8000/v1/admin/fixtures/1/result \
  -d '{"mu_goals":0,"opp_goals":1}'

# Demo / diễn tập: chỉ chạy cho hội đã đánh dấu demo
curl -H "$H" -H 'content-type: application/json' -X POST localhost:8000/v1/admin/groups/1/demo -d '{"is_demo":true}'
curl -H "$H" -H 'content-type: application/json' -X POST localhost:8000/v1/admin/replay \
  -d '{"scenario":"atletico-2026-10-13","speed":30}'          # ~3 phút sau thì hang mở
```

## Cấu trúc

```
vaohang/
  rules.py              luật hang (thua mở, hoà giữ, thắng đóng)
  services/cave.py      mở/đóng hang, vào/ra, sáng đèn, rủ anh em, vỗ vai, màn hang
  services/matches.py   lịch, hỏi kết quả theo hạn mức, nhắc trận, replay, nhập tay
  services/groups.py    hội anh em, mã mời, duyệt
  providers/            football-data.org, API-Football, hạn mức request/ngày
  pats/                 kho câu dự phòng, bộ lọc luật, Claude soạn + chấm
  api/routes.py         HTTP API
  notifier.py           FCM / outbox
```

Chưa thử với token/key thật của football-data.org và API-Football (mạng của môi trường phát triển chặn hai host này);
định dạng dữ liệu theo tài liệu công khai, cần kiểm tra lại khi có key.
