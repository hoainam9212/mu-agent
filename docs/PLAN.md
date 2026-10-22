# VAR Bàn Phím – Kế hoạch triển khai MVP

> Nguồn: proposal "VAR Bàn Phím v2". Ngày lập: 22/10/2026.
> Mục tiêu: MVP 2 tuần, 5 người → **demo** tua nhanh ×30 trận Atlético 2-0 MU (14/10)
> và **pilot thật** với 20 fan trong đêm MU – Roma (03:00 ngày 4/11, giờ VN).

---

## 0. Tóm tắt một trang

| Thành phần | Công nghệ | Người phụ trách |
|---|---|---|
| Bàn phím Android (giờ nóng, thanh soạn, giữ câu) | Fork FlorisBoard (Kotlin, Apache-2.0) | KB-1 |
| App vỏ: Biên bản VAR, lưu mã hoá, lịch nhả câu, hang, onboarding | Kotlin, Jetpack Compose, Room + Android Keystore, WorkManager, FCM | KB-2 |
| Bộ phân loại trên máy + học ngưỡng cá nhân, LLM viết lại | Python (train) → TFLite/ONNX (on-device); Claude API (viết lại) | AI |
| Máy chủ trận đấu + hang + proxy viết lại + telemetry | Python FastAPI, API-Football, Firebase Cloud Messaging | SRV |
| Gán nhãn, bộ đo 1.500 câu, tuyển & vận hành pilot, báo cáo | Label Studio / Google Sheets, notebook đo | DATA |

Mốc chính:

- **M0 – 23/10 (T6):** chốt spec, hợp đồng API, hướng dẫn gán nhãn; FlorisBoard fork build được.
- **M1 – 28/10 (T4):** chạy end-to-end bằng replay ×30 với bộ phân loại baseline.
- **M2 – 1/11 (CN):** code freeze bản RC, kết quả đo offline trên 1.500 câu.
- **M3 – 4/11 (T4) 03:00:** pilot MU – Roma; 06:30 Biên bản VAR; 5/11 báo cáo.

---

## 1. Phạm vi MVP

**Làm (in scope)**

1. Bàn phím Telex tiếng Việt, ngoài giờ nóng hoạt động như bàn phím bình thường.
2. Giờ nóng tự bật khi MU **thủng lưới** hoặc **nhận thẻ đỏ**; tắt khi hết trận (+ thời gian nguội, xem §3.2).
3. Trong giờ nóng: chữ gõ nằm ở **thanh soạn** của bàn phím; chỉ khi bấm "Gửi vào ô" mới qua VAR rồi vào ô bình luận.
4. VAR phân loại: **chửi người** → giữ (THẺ ĐỎ) · **chê chuyên môn / khác** → cho qua.
5. Câu bị giữ: lưu mã hoá trên máy, nhả lại vào sáng mai (báo thức kế tiếp, hoặc 2 tiếng nếu đang thức), tự xoá sau 7 ngày.
6. **Biên bản VAR**: Gửi / Viết lại (LLM) / Bỏ; cộng nút "VAR sai" và "Gửi nguyên văn" lúc nào cũng có.
7. **Hang**: tạo/tham gia hội anh em bằng mã mời; "Rủ anh em vào hang" → anh em nhận thông báo, trả lời bằng câu vỗ vai soạn sẵn; MU thắng trận sau → "ra khỏi hang".
8. Nhắc đổi bàn phím trước trận đêm.
9. Telemetry ẩn danh **không chứa văn bản** để đo 4 chỉ số.
10. Chế độ demo: replay trận Atlético ×30.

**Không làm trong MVP (out of scope)**

- iOS (keyboard extension của iOS giới hạn bộ nhớ và mạng nhiều hơn – để sau).
- Phát hành Google Play công khai (pilot phát qua Firebase App Distribution hoặc Play internal testing).
- Ô chat tự do trong hang (cố ý không làm – Luật VAR).
- Nhập bằng giọng nói / gõ vuốt trong giờ nóng (tắt trong giờ nóng cho đơn giản, xem §3.1).
- Các đội khác ngoài MU.

---

## 2. Kiến trúc

```
┌──────────────────────── Điện thoại Android ────────────────────────┐
│  FlorisBoard fork (IME)                                             │
│   ├─ Telex composer                                                 │
│   ├─ HotModeController ◄── trạng thái trận (FCM push + pull khi mở) │
│   ├─ StagingBar (thanh soạn)                                        │
│   └─ VarGate ──► Normalizer ► Lexicon/Rules ► TFLite model ► ngưỡng │
│          │ cho qua → InputConnection.commitText()                   │
│          │ giữ     → HoldStore (Room + AES-GCM Keystore)            │
│  App VAR (cùng APK)                                                 │
│   ├─ Biên bản VAR (Gửi / Viết lại / Bỏ / VAR sai)                   │
│   ├─ ReleaseScheduler (WorkManager), PurgeWorker (7 ngày)           │
│   ├─ Hang: nhóm, vỗ vai, ra khỏi hang                               │
│   └─ Telemetry (đếm sự kiện, không văn bản)                         │
└───────────────┬───────────────────────────────────────▲─────────────┘
                │ HTTPS (rewrite, nhóm, telemetry)       │ FCM data msg
┌───────────────▼───────────────────────────────────────┴─────────────┐
│  Máy chủ (FastAPI)                                                  │
│   ├─ MatchPoller: API-Football → MatchStateMachine → FCM topic      │
│   ├─ Replay: phát lại sự kiện đã ghi, hệ số thời gian ×N            │
│   ├─ Groups/Cave API → FCM tới thành viên                            │
│   ├─ Rewrite proxy → Claude API (không lưu log nội dung)            │
│   └─ Telemetry ingest → Postgres/SQLite → notebook đo               │
└─────────────────────────────────────────────────────────────────────┘
```

Cấu trúc repo (monorepo):

```
mu-agent/
  android/      # fork FlorisBoard + module :var (gate, store, biên bản, hang)
  server/       # FastAPI: match, replay, groups, rewrite, telemetry
  ml/           # normalizer, baseline, teacher/student, export TFLite, eval
  data/         # HƯỚNG DẪN gán nhãn, schema – KHÔNG commit dữ liệu thô
  docs/         # spec, plan, báo cáo đo
```

---

## 3. Thiết kế chi tiết theo thành phần

### 3.1 Bàn phím (KB-1)

- **Fork FlorisBoard**, giữ NOTICE/giấy phép Apache-2.0, đổi applicationId, bỏ mọi tên/logo MU.
- **Telex – kiểm tra ngay ngày 1.** Proposal ghi FlorisBoard "có Telex" nhưng chưa xác minh được.
  Nếu không có hoặc chất lượng kém: tự viết `TelexComposer` (máy trạng thái: dấu thanh s/f/r/x/j/z,
  dấu mũ aa/ee/oo/dd, aw/ow/uw, đặt dấu theo quy tắc kiểu mới/cũ) + bộ test ~300 ca. Ước lượng 1,5–2 ngày.
- **Chặn luồng commit trong giờ nóng:** mọi `commitText`/`setComposingText` đi vào `StagingBar`
  (một ô soạn nhỏ trên hàng phím, có con trỏ, xoá, dán). Nút "Gửi vào ô" ⇒ `VarGate.check(text)`.
  - Cho qua → `commitText` vào ô của app.
  - Giữ → hiện thẻ "THẺ ĐỎ – giữ đến 06:30" với 3 nút: **Rủ anh em vào hang · VAR sai · Gửi nguyên văn**.
  - Gõ vuốt, giọng nói, gợi ý từ: tắt trong giờ nóng (tránh đường commit vòng qua VAR).
- **Bỏ qua tuyệt đối** (không bật VAR, không lưu gì): `inputType` mật khẩu (TEXT/WEB/VISIBLE/NUMBER_PASSWORD),
  ô có `IME_FLAG_NO_PERSONALIZED_LEARNING` (ẩn danh), ô số/điện thoại/email.
- **Phạm vi app:** mặc định chỉ bật VAR ở app MXH + trình duyệt (Facebook, Messenger?, Instagram, Threads,
  X, TikTok, YouTube, Reddit, Chrome…), người dùng tự bật/tắt từng app. *(Câu hỏi mở Q1.)*
- **Trạng thái trận:** `HotModeController` nhận FCM; mỗi lần bàn phím mở (`onStartInputView`) nếu đang trong
  khung giờ có trận thì gọi `GET /v1/match/live` (timeout 1s) để không phụ thuộc FCM bị Doze trễ.
- Hiệu năng: phân loại < 100 ms trên máy tầm trung; model nạp lười khi vào giờ nóng.

### 3.2 Máy trạng thái "giờ nóng"

```
IDLE ──kickoff MU──► LIVE_COOL ──bàn thua / thẻ đỏ MU──► HOT
HOT ──hết trận──► (MU thắng? → IDLE) : COOLDOWN ──+60 phút──► IDLE
Bất kỳ ──người dùng tự bật "Giờ nóng"──► HOT (tắt tay được)
```

- Proposal: "bàn thua, thẻ đỏ → hết trận". Đề xuất thêm **COOLDOWN 60 phút** sau trận nếu MU không thắng,
  vì kịch bản cho thấy fan vẫn bình luận sau 90'+. *(Câu hỏi mở Q2.)*
- Cân nhắc thêm sự kiện kích hoạt: penalty chống MU, bàn thắng bị VAR huỷ. Để sau MVP nếu thiếu thời gian.

### 3.3 Lịch nhả câu (KB-2)

```
release_at =
  nếu có báo thức kế tiếp (AlarmManager.getNextAlarmClock) trong 12h tới → giờ báo thức
  ngược lại, nếu giờ địa phương 00:00–06:00                              → 07:00 hôm đó
  ngược lại (đang thức)                                                   → now + 2h
```

- Dùng múi giờ của máy (VN UTC+7, Nhật UTC+9) – có unit test cho cả hai.
- `WorkManager` với initial delay → thông báo "Biên bản VAR có N câu". Không cần quyền exact alarm.
- `PurgeWorker` chạy hằng ngày xoá câu quá 7 ngày (kể cả chưa xử lý → ghi telemetry `expired`).

### 3.4 Lưu trữ & quyền riêng tư (KB-2)

- Room DB; trường văn bản mã hoá AES-256-GCM, khoá trong Android Keystore (không xuất được).
- Mỗi bản ghi: `id, ciphertext, app_package, created_at, release_at, match_id, minute, score, score_model, arm`.
- Văn bản **chỉ rời máy** khi người dùng bấm "Viết lại" (gửi tới `/v1/rewrite`).
- "Gửi" buổi sáng: ngữ cảnh ô nhập đêm qua đã mất → **chép vào clipboard + mở lại app nguồn**
  (`app_package`) và hướng dẫn "dán vào bình luận". Đây là giới hạn của Android, cần nói rõ trong UI.
- Onboarding có màn hình đồng ý: giải thích chỉ bật trong giờ nóng, không ghi phím, telemetry gì được gửi.

### 3.5 Bộ phân loại (AI)

**Định nghĩa nhãn** (thống nhất với DATA trong hướng dẫn gán nhãn):

| Nhãn | Ví dụ | Hành động |
|---|---|---|
| `CHUI_NGUOI` – xúc phạm/nhắm vào **con người** (cầu thủ, HLV, trọng tài, fan khác, gia đình), cáo buộc nhận tiền, đe doạ, miệt thị ngoại hình/quê quán/chủng tộc | "trọng tài ăn tiền à, đồ…" | **Giữ** |
| `CHE_CHUYEN_MON` – chê lối chơi, chiến thuật, phong độ, quyết định | "hàng thủ dâng cao quá" | Cho qua |
| `KHAC` – cảm thán, tục không nhắm ai, trung tính | "đm thua rồi" | Cho qua |

Nguyên tắc "thà bỏ sót còn hơn giữ nhầm lời chê": chỉ giữ khi chắc là `CHUI_NGUOI`.

**Pipeline trên máy**

1. `Normalizer`: hạ chữ thường, chuẩn hoá Unicode (NFC), bỏ ký tự chen ("đ.m", "d m"), teencode
   (dm, đmm, vl, clgt, cc, óc chó…), gõ không dấu, tên/biệt danh cầu thủ & trọng tài → token `<NGUOI>`.
2. `Rules`: từ điển tục + mẫu "nhắm người" (mày, thằng, con, đồ + tên riêng) → đặc trưng, không quyết định một mình.
3. `Model`:
   - **v0 (ngày 3):** char n-gram TF-IDF + Logistic Regression, xuất trọng số sang JSON/TFLite – nhẹ, giải thích được.
   - **v1 (ngày 8):** teacher PhoBERT/ViSoBERT fine-tune trên ViHSD + ViHOS + dữ liệu bóng đá tự gán;
     chưng cất sang student nhỏ (BiLSTM hoặc TinyBERT 2–4 lớp, int8, mục tiêu < 15 MB, < 50 ms).
     Chỉ ship v1 nếu thắng v0 trên tập dev theo tiêu chí bên dưới; không thì ship v0.
4. **Chọn ngưỡng** trên tập dev: tối đa recall `CHUI_NGUOI` với ràng buộc tỉ lệ giữ nhầm `CHE_CHUYEN_MON` ≤ 6%
   (để còn biên an toàn dưới mức 10% của chỉ tiêu).

**Học ngưỡng riêng của bạn** (trên máy, đơn giản cho MVP):

- "VAR sai" → ngưỡng cá nhân +0,05 và thêm câu đã chuẩn hoá vào allowlist cá nhân.
- "Gửi nguyên văn" buổi sáng → +0,02. "Bỏ" / "Viết lại" → −0,01.
- Kẹp trong `[t0 − 0,05; t0 + 0,20]` để không trôi quá xa.

**Lưu ý dữ liệu:** ViHSD, ViHOS chưa xác minh được giấy phép – kiểm tra trên repo gốc trước khi dùng;
nếu chỉ cho nghiên cứu thì ghi rõ MVP là nghiên cứu/thử nghiệm. Hai bộ này không phân biệt "chê chuyên môn"
với "chửi người", nên **bắt buộc** có dữ liệu bóng đá tự gán (§3.7).

### 3.6 Viết lại bằng LLM (AI + SRV)

- `POST /v1/rewrite {text, context:{minute, score, event}}` → 2 phương án.
- Prompt: giữ nguyên ý kiến và độ "cay" về **chuyên môn**, bỏ mọi công kích cá nhân/tục, giọng fan Việt tự nhiên,
  ≤ 1,5× độ dài gốc; nếu câu không có nội dung chuyên môn → gợi ý một nhận xét chuyên môn từ ngữ cảnh trận.
- Model: Claude (vd. `claude-sonnet-5-5`; thử `claude-haiku-5-5` nếu cần nhanh/rẻ hơn) – so sánh trên 50 câu mẫu ngày 6.
- Máy chủ **không ghi log nội dung**; chỉ log độ trễ và mã lỗi. Kết quả không bao giờ tự gửi – người dùng sửa/chọn.
- Test an toàn: bộ 50 câu khó (xúc phạm nặng, chủng tộc) phải ra kết quả không còn công kích.

### 3.7 Dữ liệu & đo (DATA)

- **Hướng dẫn gán nhãn** (ngày 1–2): định nghĩa 3 nhãn, ~40 ví dụ biên, quy tắc cho câu lẫn chê + chửi (→ `CHUI_NGUOI`).
- **Thu thập:** bình luận công khai từ trang/nhóm bóng đá Việt về các trận MU gần đây; ẩn danh ngay khi thu
  (bỏ tên người bình luận, link, ID); không phân phối lại dữ liệu thô; không commit lên repo.
- **Quy mô:**
  - Tập đo cố định **1.500 câu**, phân tầng ~500 `CHE_CHUYEN_MON` / ~500 `CHUI_NGUOI` / ~500 `KHAC`.
    Khoá lại từ ngày 26/10, AI **không được nhìn**.
  - Tập train/dev bóng đá thêm ~2.000–3.000 câu (có thể dùng LLM gán nhãn sơ bộ + người duyệt để tăng tốc).
- **Độ đồng thuận:** 200 câu gán đôi (DATA + AI hoặc 1 dev), Cohen's κ ≥ 0,7; dưới ngưỡng → sửa hướng dẫn.
- Báo cáo đo kèm khoảng tin cậy 95%.

### 3.8 Máy chủ trận đấu (SRV)

- **Nguồn:** API-Football (MU team id = 33). Poll `/fixtures?live` / `/fixtures/events?fixture=…` mỗi ~15s
  **chỉ trong cửa sổ trận** (kickoff − 10' → FT + 15'). Gói Free 100 request/ngày không đủ cho live polling
  → cần gói trả phí (Pro, 7.500 req/ngày, theo trang của API-Football; xác nhận giá trên dashboard).
- `MatchStateMachine` sinh sự kiện chuẩn hoá: `kickoff, goal_for, goal_against, red_card_mu, full_time`
  (chống trùng theo `event_id`, xử lý bàn thắng bị huỷ).
- Đẩy FCM **data message ưu tiên cao** tới topic `team-33`; payload:
  `{type, fixture_id, event, minute, score:"0-1", ts}`.
- `GET /v1/match/live` → `{fixture_id, status, hot, hot_since, minute, score}` cho client pull.
- **Replay:** ghi lại sự kiện trận Atlético 2-0 MU (14/10) từ API-Football thành JSON; `POST /v1/replay {fixture, speed:30}`
  phát lại lên topic `demo-…` riêng (không đụng người dùng thật). 90 phút ×30 ≈ 3 phút.
- **Lịch trận:** job hằng ngày lấy lịch MU → gửi nhắc "Bật VAR Bàn Phím" 30 phút trước các trận đá trong 00:00–06:00 giờ máy.
- **Hạ tầng:** 1 container (Cloud Run min-instance=1 trong cửa sổ trận, hoặc VPS nhỏ), Postgres/SQLite, Firebase project.
- **Giám sát đêm pilot:** dashboard đơn giản (độ trễ API-Football → FCM, số máy nhận, lỗi rewrite).

### 3.9 Hang – anh em vỗ vai (KB-2 + SRV)

- `POST /v1/groups` → mã mời 6 ký tự / QR; `POST /v1/groups/join`. Tối đa ~10 người/nhóm.
- `POST /v1/cave/enter {group_id}` → FCM tới thành viên **đã bật VAR**: "[Biệt danh] đang vào hang 🕳️".
- Thành viên chọn **1 trong ~8 câu vỗ vai soạn sẵn** (mỗi câu có đồng cảm + góc nhìn mới, vd.
  "Làm fan MU còn chịu được cơ mà." + "Hiệp 2 thủ kém, chứ trọng tài không đá thay"). Không có ô nhập tự do.
- Không ai trong nhóm thấy câu bị giữ; server không nhận văn bản đó.
- Sau trận MU thắng kế tiếp: server gửi "Ra khỏi hang" tới mọi nhóm có người vào hang trong 7 ngày.

### 3.10 Telemetry & chỉ số (SRV + DATA)

Sự kiện (ID ẩn danh ngẫu nhiên theo cài đặt, **không có văn bản**):
`hot_on, check{verdict, score_bucket, latency_ms}, hold{arm}, var_wrong, send_verbatim_now,
morning_decision{send|rewrite_send|drop|expired}, cave_enter, pat_received, keyboard_enabled_after_loss`.

| Chỉ số (proposal §5) | Cách đo | Đo được trong MVP? |
|---|---|---|
| 1. ≥ 50% câu bị giữ được Bỏ/Viết lại | `morning_decision` ∈ {drop, rewrite_send} / tổng câu có quyết định; báo riêng `expired`, `var_wrong` | Có tín hiệu sớm từ pilot (mẫu nhỏ) |
| 2. < 10% giữ nhầm lời chê, ≥ 60% bắt câu xúc phạm | Offline trên tập 1.500 câu | **Có – tiêu chí chính của MVP** |
| 3. ≥ 70% vẫn bật VAR sau 4 trận thua | Theo dõi `keyboard_enabled_after_loss` qua nhiều tuần | Không – cần giai đoạn sau |
| 4. Nút "Rủ" → +10 điểm % Bỏ/Viết lại | Random 50/50 theo từng lần giữ (`arm`), ~800 lần giữ | Không – 20 người × 1 đêm không đủ; cài sẵn để chạy tiếp |

---

## 4. Lịch 2 tuần

| Ngày | KB-1 (bàn phím) | KB-2 (app vỏ) | AI | SRV | DATA |
|---|---|---|---|---|---|
| **T5 22/10** | Fork + build FlorisBoard, **kiểm tra Telex** | Khung app Compose, module `:var` | Chốt nhãn với DATA, dựng repo `ml/` | Firebase project, key API-Football, khung FastAPI | Nháp hướng dẫn gán nhãn |
| **T6 23/10** | StagingBar + chặn commit | Room + Keystore, schema HoldStore | Normalizer + teencode v0 | Hợp đồng API (OpenAPI), ghi dữ liệu trận Atlético | Chốt hướng dẫn, bắt đầu thu thập · **M0** |
| T7–CN 24–25/10 | (Telex composer nếu cần) · bỏ qua ô mật khẩu | ReleaseScheduler + PurgeWorker | Baseline v0 trên ViHSD/ViHOS + 500 câu đầu | MatchStateMachine + FCM + `/match/live` | Gán 1.000 câu, κ trên 200 câu |
| T2 26/10 | HotModeController (FCM + pull) | Biên bản VAR (UI) | Xuất v0 sang máy, đo độ trễ | Replay ×30 | **Khoá tập đo 1.500 câu** |
| T3 27/10 | Tích hợp VarGate ↔ v0 | Thẻ THẺ ĐỎ, VAR sai, Gửi nguyên văn | Teacher PhoBERT/ViSoBERT | `/rewrite` + so sánh model | Gán thêm tập train bóng đá |
| **T4 28/10** | Sửa lỗi E2E | Clipboard + mở app nguồn | Prompt viết lại v1 | `/groups`, `/cave/*` | Đo v0 trên dev · **M1: E2E replay ×30** |
| T5 29/10 | Phạm vi app, tắt vuốt/giọng nói giờ nóng | UI Hang, vỗ vai, ra khỏi hang | Student distill + int8 | Telemetry ingest, lịch trận + nhắc | Câu vỗ vai soạn sẵn (cùng KB-2) |
| T6 30/10 | Hiệu năng, thiết bị Samsung/Xiaomi | Onboarding + màn đồng ý, telemetry client | Chọn ngưỡng, học ngưỡng cá nhân | Dashboard giám sát | Tuyển 20 fan pilot (Android) |
| T7 31/10 | Dry-run nội bộ (trận MU cuối tuần nếu có, hoặc replay) | ← | Chọn v0/v1 | ← | Kịch bản demo |
| **CN 1/11** | **Code freeze RC** | ← | **Đo trên tập 1.500 câu (một lần)** | Load test FCM | Báo cáo đo offline · **M2** |
| T2–T3 2–3/11 | Sửa lỗi chặn | Phát APK qua App Distribution | Hỗ trợ | Trực chuẩn bị | Onboard pilot (cài, đổi bàn phím, vào nhóm) |
| **T4 4/11** | 03:00 trực đêm pilot | 06:30 Biên bản VAR | Theo dõi verdict | Theo dõi độ trễ | Khảo sát sáng · **M3** |
| T5 5/11 | Retro | ← | ← | ← | **Báo cáo pilot** |

Quy tắc tuần 2: từ 1/11 chỉ sửa lỗi; tính năng chưa xong (penalty trigger, v1 model…) để sau pilot.

---

## 5. Kiểm thử

- **Unit:** TelexComposer (~300 ca), máy trạng thái giờ nóng, `release_at` (báo thức / đêm / đang thức, UTC+7 & UTC+9),
  bỏ qua ô mật khẩu, xoá sau 7 ngày, kẹp ngưỡng cá nhân, chống trùng sự kiện trận.
- **Golden set phân loại** chạy trong CI: ~100 câu chê chuyên môn **phải** cho qua, ~100 câu chửi người rõ ràng phải giữ.
- **E2E:** replay ×30 trên thiết bị thật → kiểm tra thứ tự: bật giờ nóng phút 1 → giữ câu phút 74 → cho qua câu chê
  → rủ hang → nhả lúc "06:30" (giờ giả lập) → Viết lại → Gửi.
- **Thiết bị:** ≥ 3 máy (Pixel/Android gốc, Samsung One UI, Xiaomi MIUI/HyperOS – hay giết tiến trình nền).
- **An toàn LLM:** 50 câu khó cho `/rewrite`, kiểm tay.

## 6. Rủi ro & phương án

| Rủi ro | Ảnh hưởng | Phương án |
|---|---|---|
| FlorisBoard không có Telex tốt | Chậm 2 ngày | Kiểm tra ngày 1; tự viết composer |
| FCM trễ do Doze / OEM giết app | Giờ nóng bật muộn | Pull khi mở bàn phím; FCM ưu tiên cao; hướng dẫn tắt tối ưu pin cho pilot |
| Model giữ nhầm lời chê > 10% | Mất lòng tin | Ngưỡng bảo thủ, VAR sai 1 chạm, ship v0 nếu v1 không chắc |
| Thiếu dữ liệu bóng đá có nhãn | Model yếu ở teencode bóng đá | LLM gán nhãn sơ bộ + người duyệt; ưu tiên tập đo 1.500 trước |
| Người dùng ngại đổi bàn phím | Pilot ít dữ liệu | Nhắc trước trận + nút mở bộ chọn bàn phím; onboarding trực tiếp |
| Giấy phép ViHSD/ViHOS | Hạn chế dùng thương mại | Kiểm tra ngày 1; ghi rõ mục đích nghiên cứu |
| API-Football trễ/lỗi đêm pilot | Không bật giờ nóng | Nút bật giờ nóng thủ công; người trực có thể bắn sự kiện tay qua admin endpoint |
| Pilot 20 người không đủ chứng minh chỉ số 1, 3, 4 | Kết luận yếu | Nói rõ trong báo cáo; chỉ số 2 là tiêu chí chính của MVP |
| MU thắng đậm, không có giờ nóng | Pilot không có dữ liệu | Rủi ro "tốt"; vẫn có replay demo |

## 7. Câu hỏi mở cần chốt (trước 23/10)

- **Q1.** Bật VAR ở mọi app hay chỉ app MXH + trình duyệt? (Đề xuất: danh sách MXH + trình duyệt, người dùng chỉnh.)
- **Q2.** Giờ nóng kết thúc đúng lúc hết trận hay thêm 60 phút nguội nếu MU không thắng? (Đề xuất: +60 phút.)
- **Q3.** Câu tục không nhắm ai ("đm thua rồi") – cho qua? (Đề xuất: cho qua, đúng tinh thần "thà bỏ sót".)
- **Q4.** Ngân sách: gói API-Football trả phí + chi phí Claude API cho viết lại + Firebase (gói miễn phí đủ cho pilot).
- **Q5.** Tên app/biểu tượng không dùng logo MU – ai duyệt thiết kế?
