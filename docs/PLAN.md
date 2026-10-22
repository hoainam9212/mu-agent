# Vào Hang – Kế hoạch triển khai bản thử 2 tuần

> Nguồn: proposal "Vào Hang". Ngày lập: 22/10/2026.
> Mục tiêu: app Android + máy chủ theo dõi trận (API-Football) + AI gợi ý câu vỗ vai,
> chạy thật với **20 fan** trong đêm **MU – Roma, 03:00 ngày 4/11** (giờ VN).
> Giả định đội 5 người (2 Android · 1 máy chủ · 1 AI · 1 nội dung/pilot) – chỉnh lại nếu khác.

---

## 0. Tóm tắt

Vòng lặp sản phẩm: **MU thua → hang tự mở → rủ anh em đang thức → vỗ vai bằng câu soạn sẵn →
hang tự tắt đèn sau 30 phút → MU thắng trận sau → cả hội ra khỏi hang.**

| Thành phần | Công nghệ | Phụ trách |
|---|---|---|
| App Android: thông báo, màn hang, vỗ vai, tắt đèn, ra hang, nhóm | Kotlin, Jetpack Compose, FCM, WorkManager | AND-1, AND-2 |
| "Ai đang thức": tín hiệu trên máy + báo thức | PowerManager, NotificationManager (DND), AlarmManager | AND-2 |
| Máy chủ: theo dõi trận, mở/đóng hang, nhóm, đẩy thông báo | Python FastAPI, Postgres, API-Football, Firebase Cloud Messaging | SRV |
| AI gợi ý câu vỗ vai theo đúng trận + lớp kiểm duyệt | Claude API + bộ lọc luật + kho câu dự phòng | AI |
| Kho câu dự phòng, hướng dẫn giọng văn, pilot, đo, khảo sát | Google Sheets / notebook | CONTENT |

Mốc chính:

- **M0 – 23/10 (T6):** chốt spec, hợp đồng API, luật "ai đang thức", hướng dẫn giọng văn câu vỗ vai.
- **M1 – 28/10 (T4):** chạy hết vòng lặp bằng replay trận Atlético 2-0 MU (14/10) tua nhanh.
- **M2 – 1/11 (CN):** dừng thêm tính năng; diễn tập với nhóm pilot.
- **M3 – 4/11 (T4) ~04:00:** đêm thật MU – Roma; 5/11 báo cáo.

---

## 1. Phạm vi

**Làm**

1. Tạo/tham gia **hội anh em** bằng mã mời; hai bên cùng đồng ý mới vào chung một hang.
2. Máy chủ **canh trận MU** trực tiếp; còi mãn cuộc mà MU thua (hoặc hoà ngay sau một trận thua) thì **tự mở hang** cho từng hội.
3. **Chỉ gọi người đang thức**; người đã ngủ hoặc đang bận thì sáng mới báo nhẹ.
4. Thông báo "Hang đã mở. Tuấn, Hùng và Long cũng đang thức" + nút **Rủ anh em vào hang** / **Thôi, ngủ luôn**.
5. Màn hang: avatar người trong hang, các cái vỗ vai nhận được, nút **Vỗ vai lại** (chọn 1 trong 3–4 câu soạn sẵn).
6. **AI soạn câu vỗ vai** theo tỷ số, đối thủ, chuỗi trận; tự trào về MU, có góc nhìn mới, không chê ai.
7. **Tắt đèn**: hang tự đóng sau 30 phút; màn hình "Báo thức của bạn: 6:30, còn kịp ngủ 2 tiếng".
8. **Ra khỏi hang**: trận MU thắng kế tiếp thì gọi cả hội ra cùng lúc.
9. Nhắc "Đêm nay có trận" trước các trận đêm.
10. Telemetry ẩn danh để đo 3 chỉ số; khảo sát ngắn.
11. Chế độ demo/diễn tập: replay trận Atlético với hệ số thời gian.

**Không làm (cố ý)**

- Ô chat tự do, gửi ảnh, chuyển tiếp ra ngoài.
- Bảng xếp hạng, điểm danh người vắng, "ai buồn nhất".
- iOS, phát hành Google Play công khai (pilot phát qua Firebase App Distribution / Play internal testing).
- Đội khác ngoài MU. Logo MU.

---

## 2. Kiến trúc

```
┌─────────────── Máy chủ (FastAPI + Postgres) ────────────────┐
│ FixtureScheduler: lịch trận MU, nhắc trước trận đêm         │
│ MatchPoller: API-Football ~15s trong cửa sổ trận            │
│   └► MatchStateMachine → FT & MU thua → CaveService.open()  │
│                         → FT & MU thắng → CaveService.exit()│
│ PatService: gọi Claude 1 lần/trận → lọc → kho dự phòng      │
│ CaveService: phiên hang theo hội, đóng sau 30', ra hang     │
│ Groups API, Presence API, Telemetry API, Replay API         │
└──────────────┬───────────────────────────────▲──────────────┘
               │ FCM data message (ưu tiên cao) │ HTTPS
┌──────────────▼───────────────────────────────┴──────────────┐
│ App Android                                                  │
│  PresenceProbe: màn hình bật? DND? báo thức? điểm danh trận? │
│  Thông báo "Hang đã mở" · Màn hang (FLAG_SECURE)             │
│  Vỗ vai / Vỗ vai lại · Tắt đèn · Ra khỏi hang                 │
│  Hội anh em (mã mời/QR) · Cài đặt · Telemetry                 │
└──────────────────────────────────────────────────────────────┘
```

Cấu trúc repo:

```
mu-agent/
  android/   # app Vào Hang
  server/    # FastAPI: match, cave, groups, presence, pats, telemetry, replay
  ai/        # prompt, bộ lọc, bộ test câu vỗ vai, kho câu dự phòng
  docs/      # spec, plan, báo cáo
```

---

## 3. Thiết kế chi tiết

### 3.1 Theo dõi trận (SRV)

- API-Football, MU team id = 33. Job hằng ngày lấy lịch trận; trong cửa sổ **kickoff − 10' → FT + 15'**
  poll `/fixtures?id=…` mỗi ~15s. Gói Free (100 request/ngày) không đủ → cần gói trả phí
  (Pro 7.500 request/ngày theo hướng dẫn của API-Football; xác nhận giá trên dashboard).
- `MatchStateMachine`: `SCHEDULED → LIVE → FINISHED`. Coi là hết trận khi status ∈ {FT, AET, PEN}.
  Chống xử lý trùng theo `fixture_id`; nếu API trễ quá 10 phút sau giờ dự kiến hết trận thì cảnh báo người trực.
- Kết quả:
  - **Thua → mở hang.**
  - **Hoà mà trận trước thua → vẫn mở hang** ("vẫn chưa ra được hang"). Hoà mà trận trước không thua → không làm gì.
    "Trận trước" = trận chính thức liền trước của MU ở **mọi giải** (lấy từ `/fixtures?team=33&last=2`, bỏ giao hữu).
  - **Thắng → ra khỏi hang** (cho các hội có hang mở trong 14 ngày qua).
  - Thắng/thua trên chấm luân lưu (status PEN) tính theo kết quả luân lưu.

  ```python
  def should_open_cave(result: str, prev_result: str | None) -> bool:
      return result == "L" or (result == "D" and prev_result == "L")
  ```
- **Admin endpoint** để người trực bắn sự kiện tay nếu API-Football lỗi trong đêm pilot.
- **Replay:** ghi sự kiện trận Atlético 2-0 MU (14/10) thành JSON; `POST /v1/replay {fixture, speed}` phát lại
  cho một nhóm demo riêng, không đụng người dùng thật.

### 3.2 "Ai đang thức" – phần agent quan trọng nhất (AND-2 + SRV)

Khi hết trận, máy chủ gửi **FCM im lặng** (data message) tới mọi thành viên của các hội. App tự đánh giá trên máy
rồi báo `POST /v1/presence {fixture_id, state}` với `state ∈ {awake, asleep, busy}`. Không gửi dữ liệu thô.

Luật đề xuất cho MVP (đơn giản, giải thích được):

```
asleep  nếu: đang bật Không làm phiền / chế độ ngủ (getCurrentInterruptionFilter ≠ ALL)
        hoặc người dùng đã bấm "Thôi, ngủ luôn"/"Tối nay không xem" cho trận này
awake   nếu: màn hình đang bật (PowerManager.isInteractive)
        hoặc người dùng đã "điểm danh" trận này (bấm "Đêm nay xem" ở thông báo trước trận)
             và có dùng điện thoại trong 15 phút gần nhất (mở app / tương tác thông báo)
busy    nếu: giờ địa phương nằm trong "giờ đi làm" người dùng tự đặt (vd. fan ở Nhật 08:00–18:00)
còn lại  → asleep (thà không gọi còn hơn đánh thức)
```

- Người `asleep`/`busy`: **không rung, không kêu**. Gửi một thông báo im lặng vào sáng
  (giờ báo thức kế tiếp, mặc định 07:00): "Đêm qua MU thua 0-2. Anh em đã vào hang, có 2 cái vỗ vai cho bạn."
- Danh sách "cũng đang thức" chỉ hiện người `awake` trong **cùng hội**.
- Kiểm tra trên ≥ 3 dòng máy (Pixel, Samsung, Xiaomi) vì FCM im lặng có thể bị trễ khi máy ở chế độ Doze;
  dùng FCM ưu tiên cao, và khi người dùng mở app thì app tự kéo trạng thái hang.

### 3.3 Phiên hang (SRV + AND-1)

- `CaveSession {group_id, fixture_id, opened_at, closes_at, members[]}`; mở cho **mỗi hội có ≥ 1 người awake**.
- Thông báo "Hang đã mở…" chỉ tới người awake. **Rủ anh em vào hang** → gửi lời mời tới người awake còn lại
  trong hội. **Thôi, ngủ luôn** → ghi `asleep`, không gửi gì thêm.
- **Đóng hang:** `closes_at = opened_at + 30'`. Màn tắt đèn tính riêng cho từng người theo báo thức của họ
  (`AlarmManager.getNextAlarmClock()`): "Báo thức của bạn: 6:30. Còn kịp ngủ 2 tiếng." Không có báo thức
  thì bỏ dòng này. Sau khi đóng: không gửi thêm thông báo nào trong đêm.
- **Ra khỏi hang:** khi MU thắng, gửi "RA KHỎI HANG!" tới **mọi thành viên** của các hội đó cùng lúc
  (trừ giờ ngủ/giờ làm theo luật §3.2 → dời tới sáng), nút "Ra hang ăn mừng" mở màn ăn mừng.
- Màn hang bật `FLAG_SECURE` (chặn chụp màn hình), không có nút chia sẻ.

### 3.4 Câu vỗ vai do AI soạn (AI)

**Cách tạo** – một lần mỗi trận, ở máy chủ, không theo từng người:

1. Lúc hết trận, `PatService` gom **dữ kiện đã kiểm chứng** từ API: tỷ số, đối thủ, giải, chuỗi thua/không thắng,
   thứ hạng hiện tại, vài cột mốc lịch sử do CONTENT soạn sẵn (vd. "mùa 24-25 hạng 15").
2. Gọi Claude (vd. `claude-sonnet-5-5`; thử `claude-haiku-5-5` nếu cần nhanh hơn) sinh ~12 câu.
   Prompt bắt buộc: **tự trào về MU**, mỗi câu có **đồng cảm + một góc nhìn mới**, ≤ 120 ký tự, giọng fan Việt,
   **chỉ dùng dữ kiện được cung cấp** (không tự bịa số liệu).
3. **Bộ lọc** (không qua được thì loại câu):
   - Không chứa tên cầu thủ/HLV/trọng tài của trận (danh sách lấy từ API), không nhắc fan đội khác.
   - Không chê/đổ lỗi (từ điển + một lượt Claude chấm "câu này có chê ai không?").
   - Số liệu trong câu phải khớp dữ kiện đầu vào.
4. Chọn 8 câu tốt nhất; **thiếu thì bù từ kho dự phòng** (~40 câu CONTENT viết sẵn, có nhãn theo kịch bản:
   thua 1 bàn, thua đậm, thua phút cuối, chuỗi thua, **hoà sau trận thua** – "vẫn chưa ra được hang"…). API lỗi → dùng 100% kho dự phòng.
5. Để kịp lúc 4h sáng: **sinh trước** bộ câu cho các kịch bản khả dĩ ngay khi hết hiệp 1 và phút 80,
   lúc hết trận chỉ cần chọn đúng bộ và chèn tỷ số.

- Mỗi người thấy 3–4 câu ngẫu nhiên từ 8 câu; "Vỗ vai lại" cũng chọn từ đó.
- **Bộ test** trước pilot: chạy 20 trận thua cũ của MU, CONTENT chấm tay; mục tiêu 0 câu chê người, 0 số liệu sai.

### 3.5 Hội anh em (AND-1 + SRV)

- `POST /v1/groups` → mã mời 6 ký tự + QR; `POST /v1/groups/join` → chủ hội duyệt (hai bên đồng ý).
- 2–10 người/hội, một người có thể ở nhiều hội. Rời hội bất kỳ lúc nào.
- Hồ sơ tối thiểu: biệt danh + chữ cái avatar. Đăng nhập ẩn danh (Firebase Anonymous Auth) cho pilot.

### 3.6 Quyền riêng tư (tất cả)

- Không đọc nội dung tin nhắn, không truy cập danh bạ, không lưu vị trí.
- Trạng thái thức/ngủ chỉ lưu dạng `awake/asleep/busy` theo từng trận, xoá sau 30 ngày.
- Không bán/chia sẻ dữ liệu cảm xúc cho quảng cáo, cá cược; ghi rõ trong màn đồng ý khi cài.

### 3.7 Đo (CONTENT + SRV)

Sự kiện telemetry (ID ẩn danh): `cave_opened, invite_sent, cave_joined, pat_sent, pat_received,
lights_out_shown, screen_off_after_ft, exit_cave_opened, survey_answer`.

| Chỉ số (proposal) | Cách đo |
|---|---|
| % lần hang mở có ≥ 1 cái vỗ vai | `CaveSession` có ≥ 1 `pat_sent` / tổng hang mở |
| % tắt máy trong 30 phút sau còi | Trong lúc hang mở, app chạy dịch vụ nền ngắn (có thông báo "Hang đang mở") nghe sự kiện màn hình tắt; tính "tắt máy" = màn hình tắt trước FT + 30' và **không bật lại ≥ 20 phút** |
| "Đỡ cay một mình" sau 1 tháng | Khảo sát 3 câu (thang 1–5) sau đêm pilot và sau 1 tháng |

Lưu ý: chỉ số 2 không có nhóm đối chứng → khảo sát thêm "bình thường sau trận thua bạn thức thêm bao lâu?" lúc onboarding
để có mốc so sánh thô.

---

## 4. Lịch 2 tuần

| Ngày | AND-1 | AND-2 | SRV | AI | CONTENT |
|---|---|---|---|---|---|
| **T5 22/10** | Khung app Compose, FCM | Thử API thức/ngủ trên 3 dòng máy | FastAPI, Postgres, Firebase, key API-Football | Nháp prompt, danh sách dữ kiện | Hướng dẫn giọng văn câu vỗ vai |
| **T6 23/10** | Hội anh em (UI) | PresenceProbe v0 | Hợp đồng API (OpenAPI), ghi dữ liệu trận Atlético | Bộ lọc tên người | 20 câu dự phòng đầu · **M0** |
| T7–CN 24–25/10 | Thông báo "Hang đã mở" | Báo thức, giờ đi làm, DND | MatchStateMachine, CaveService | Sinh câu từ dữ kiện thật | Cột mốc lịch sử MU đã kiểm chứng |
| T2 26/10 | Màn hang + vỗ vai | Thông báo im lặng buổi sáng | Groups, Presence API | Lượt chấm "có chê ai không" | Đủ 40 câu dự phòng |
| T3 27/10 | Tắt đèn | Dịch vụ nền đo tắt máy | Replay ×N | Sinh trước theo kịch bản | Chấm câu AI vòng 1 |
| **T4 28/10** | Ra khỏi hang | Telemetry client | PatService tích hợp | Sửa prompt | **M1: replay chạy hết vòng lặp** |
| T5 29/10 | FLAG_SECURE, onboarding + màn đồng ý | Nhắc trước trận đêm | Lịch trận + nhắc | Bộ test 20 trận thua cũ | Tuyển 20 fan (Android, có bạn MU) |
| T6 30/10 | Sửa lỗi UI | Kiểm thử Samsung/Xiaomi | Telemetry, dashboard trực | Chấm & khoá prompt | Soạn khảo sát |
| T7 31/10 | Dry-run nội bộ (trận MU cuối tuần nếu có, hoặc replay) | ← | ← | ← | Kịch bản demo |
| **CN 1/11** | **Dừng thêm tính năng** | ← | Load test FCM | ← | **M2** |
| T2 2/11 | Phát APK | Hướng dẫn tắt tối ưu pin | Trực thử | ← | Onboard pilot, lập hội |
| T3 3/11 21:00 | **Diễn tập** với pilot bằng replay (phòng khi đêm thật không mở hang) | ← | ← | ← | Khảo sát diễn tập |
| **T4 4/11** | 03:00–05:00 trực đêm pilot | ← | ← | ← | Khảo sát sáng · **M3** |
| T5 5/11 | Retro | ← | ← | ← | **Báo cáo pilot** |

Từ 1/11 chỉ sửa lỗi.

---

## 5. Kiểm thử

- **Unit:** luật thức/ngủ (DND, màn hình, điểm danh, giờ làm, múi giờ VN UTC+7 & Nhật UTC+9),
  `closes_at`, dòng "còn kịp ngủ X tiếng", chống trùng sự kiện hết trận, chọn ra hang khi thắng, `should_open_cave` (L; D sau L; D sau D/W; thua/thắng luân lưu; trận trước là giao hữu).
- **AI:** bộ test 20 trận thua cũ + 30 câu "bẫy" (prompt chứa tên cầu thủ, số liệu sai) → bộ lọc phải loại.
- **E2E:** replay trên thiết bị thật: 1 máy thức, 1 máy DND, 1 máy tắt màn hình → chỉ máy thức được gọi,
  2 máy còn lại nhận thông báo im lặng buổi sáng; hang đóng đúng 30'; trận hoà giả lập sau trận thua → hang vẫn mở; trận thắng giả lập → cả hội nhận "Ra khỏi hang".

## 6. Rủi ro & phương án

| Rủi ro | Phương án |
|---|---|
| **MU thắng đêm 4/11, hoặc hoà mà trận ngay trước đó không thua → không có hang** (proposal gọi đây là "rủi ro lớn nhất") | Diễn tập bằng replay tối 3/11 với chính nhóm pilot để có dữ liệu vòng lặp; nếu MU thắng thật thì đo luồng "Ra khỏi hang" |
| Đoán sai thức/ngủ → đánh thức người đang ngủ | Mặc định coi là ngủ; thông báo hang không kêu nếu không chắc; nút "Đừng gọi tôi đêm nay" |
| FCM im lặng trễ do Doze/OEM | FCM ưu tiên cao; hướng dẫn tắt tối ưu pin cho pilot; app tự kéo trạng thái khi mở |
| AI bịa số liệu hoặc lỡ chê cầu thủ | Chỉ dùng dữ kiện đầu vào, bộ lọc 2 lớp, kho dự phòng |
| API-Football trễ/lỗi lúc 4h sáng | Người trực + admin endpoint bắn sự kiện tay |
| 20 người chưa đủ để kết luận | Nói rõ trong báo cáo; xem pilot là kiểm chứng vòng lặp + tín hiệu sớm |

## 7. Câu hỏi mở (chốt trước 23/10)

- ~~**Q1.** MU **hoà** có mở hang không?~~ **Đã chốt 22/10:** hoà mà trận trước thua thì vẫn mở hang (§3.1).
  Còn một trường hợp biên: thua → hoà → hoà (trận trước là hoà). Theo luật hiện tại trận thứ ba **không** mở hang.
- **Q2.** Mockup có avatar "N" mờ (người đang ngủ), trong khi luật nói "không điểm danh ai vắng".
  Đề xuất: **chỉ hiện người đang trong hang**, không hiện người vắng.
- **Q3.** Ra khỏi hang: chỉ hội đã từng vào hang, hay mọi người dùng? (Đề xuất: mọi hội có hang mở trong 14 ngày qua.)
- **Q4.** Đóng hang cứng 30 phút, hay sớm hơn nếu báo thức quá gần (vd. còn < 2 tiếng)?
- **Q5.** Ngân sách: gói API-Football trả phí + Claude API (chỉ vài lượt gọi mỗi trận) + Firebase (gói miễn phí đủ cho pilot).
