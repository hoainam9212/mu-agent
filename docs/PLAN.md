# Vào Hang – Kế hoạch triển khai bản thử 2 tuần

> Nguồn: proposal "Vào Hang". Ngày lập: 22/10/2026.
> Mục tiêu: app Android + máy chủ theo dõi trận (API-Football) + AI gợi ý câu vỗ vai,
> chạy thật với **20 fan** trong đêm **MU – Roma, 03:00 ngày 4/11** (giờ VN).
> Giả định đội 5 người (2 Android · 1 máy chủ · 1 AI · 1 nội dung/pilot) – chỉnh lại nếu khác.

---

## 0. Tóm tắt

Vòng lặp sản phẩm: **MU thua → hang mở → ai muốn thì vào (không bắt buộc) → vỗ vai bằng câu soạn sẵn →
tắt đèn sau 30 phút, nhưng hang vẫn mở → ai muốn ra lúc nào cũng được → MU thắng thì hang đóng, ai còn trong hang
được đưa ra cùng lúc.**

Ba khái niệm tách biệt (chốt 22/10, khác proposal gốc ở chỗ hang không tự đóng sau 30 phút):

| Khái niệm | Ai quyết | Khi nào |
|---|---|---|
| **Hang mở / đóng** | Kết quả trận | Mở khi MU thua; giữ nguyên khi hoà; **chỉ đóng khi MU thắng** |
| **Ở trong / ngoài hang** | **Người dùng** | Vào khi muốn (lúc hang mở); ra lúc nào cũng được; MU thắng thì tự động ra |
| **Sáng đèn / tắt đèn** | Agent | Sáng đèn 30 phút sau mỗi trận không thắng khi hang mở; ngoài lúc đó hang im lặng |

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
2. Máy chủ **canh trận MU** trực tiếp; còi mãn cuộc mà MU thua thì **mở hang** cho từng hội; hoà thì hang giữ nguyên;
   chỉ khi MU thắng hang mới đóng.
3. **Chỉ gọi người đang thức**; người đã ngủ hoặc đang bận thì sáng mới báo nhẹ.
4. Sau mỗi trận không thắng khi hang mở: người ở ngoài nhận "Hang đang mở. Tuấn, Hùng đang trong hang" +
   nút **Vào hang** / **Thôi, ngủ luôn**; vào hay không là **tự chọn**. Người trong hang có nút **Rủ anh em vào hang**.
5. Màn hang: avatar người trong hang, các cái vỗ vai nhận được, nút **Vỗ vai lại** (chọn 1 trong 3–4 câu soạn sẵn),
   nút **Ra khỏi hang** (lúc nào cũng có, ra lặng lẽ, không báo ai).
6. **AI soạn câu vỗ vai** theo tỷ số, đối thủ, chuỗi trận; tự trào về MU, có góc nhìn mới, không chê ai.
7. **Tắt đèn**: 30 phút sau còi, hang im lặng (không vỗ vai, không thông báo) nhưng **vẫn mở**;
   màn hình "Báo thức của bạn: 6:30, còn kịp ngủ 2 tiếng". Trận không thắng tiếp theo thì hang sáng đèn lại.
8. **Ra khỏi hang**: MU thắng thì hang đóng, **chỉ những ai đang trong hang** được đưa ra cùng lúc kèm màn ăn mừng.
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
│   └► MatchStateMachine → FT & MU thua/hoà → CaveService.light()│
│                         → FT & MU thắng → CaveService.close()│
│ PatService: gọi Claude 1 lần/trận → lọc → kho dự phòng      │
│ CaveService: hang theo hội, vào/ra, sáng đèn 30', đóng khi thắng│
│ Groups API, Presence API, Telemetry API, Replay API         │
└──────────────┬───────────────────────────────▲──────────────┘
               │ FCM data message (ưu tiên cao) │ HTTPS
┌──────────────▼───────────────────────────────┴──────────────┐
│ App Android                                                  │
│  PresenceProbe: màn hình bật? DND? báo thức? điểm danh trận? │
│  Thông báo "Hang đang mở" · Vào/Ra hang · Màn hang (FLAG_SECURE)│
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

- **Chốt 22/10: dùng API miễn phí trước.** Hang chỉ cần **kết quả cuối trận**, không cần diễn biến, nên:
  lịch trận 1 request mỗi 12 tiếng; kết quả bắt đầu hỏi ở **phút 105** sau giờ bóng lăn, **mỗi 3 phút**, tới khi
  có kết quả (bỏ cuộc sau 6 tiếng). Mỗi trận tốn khoảng 5–40 request, vừa gói Free. Đổi lại, hang mở trễ
  tối đa ~3 phút sau còi.
- Hai nguồn, chọn bằng biến môi trường (`server/README.md`):
  - **football-data.org** (khuyên dùng cho gói Free): có Premier League + Champions League mùa hiện tại,
    10 request/phút; **không có** FA Cup, League Cup, Europa League → các trận đó người trực nhập tay.
  - **API-Football** (MU = 33): gói Free 100 request/ngày; theo nhiều báo cáo gói Free có thể **không có mùa hiện tại**
    – lỗi trả về được hiện ở `/v1/admin/status`. Cần thử với key thật trước pilot.
- Trạng thái trận: `SCHEDULED → LIVE → FINISHED` (FT, AET, PEN, AWD). Mỗi trận chỉ xử lý một lần.
  Trận cũ thấy được lúc máy chủ mới khởi động chỉ được ghi nhận, không mở/đóng hang muộn.
- Kết quả trận chính thức (mọi giải, bỏ giao hữu) cập nhật trạng thái hang:
  - **Thua → hang mở** (nếu đang mở thì vẫn mở) và **sáng đèn**.
  - **Hoà → hang giữ nguyên.** Đang mở thì sáng đèn lại ("vẫn chưa ra được hang"); đang đóng thì không làm gì.
    Nhờ vậy thua → hoà → hoà thì hang vẫn mở suốt.
  - **Thắng → hang đóng**, ai đang trong hang được đưa ra.
  - Thắng/thua trên chấm luân lưu (status PEN) tính theo kết quả luân lưu.

  ```python
  def next_cave_open(is_open: bool, result: str) -> bool:
      if result == "W":
          return False      # đóng hang, mọi người trong hang ra
      if result == "L":
          return True
      return is_open        # hoà: giữ nguyên

  def lights_on(is_open_after: bool, result: str) -> bool:
      return is_open_after and result != "W"   # sáng đèn 30' sau trận
  ```
- **Nhập tay** (`/v1/admin/fixtures`, `/v1/admin/fixtures/{id}/result`) khi API lỗi hoặc giải không có trong gói Free.
- **Replay:** `POST /v1/admin/replay {scenario, speed}` – trận Atlético 2-0 MU (13/10, giờ VN là 14/10) tua nhanh,
  chỉ chạy cho **hội demo** (`/v1/admin/groups/{id}/demo`), không đụng người dùng thật. Có thêm `draw-demo`, `win-demo`.

### 3.2 "Ai đang thức" – phần agent quan trọng nhất (AND-2 + SRV)

Khi hết trận, máy chủ gửi **FCM im lặng** (data message) tới mọi thành viên của các hội. App tự đánh giá trên máy
rồi báo `POST /v1/presence {fixture_id, state}` với `state ∈ {awake, asleep, busy}`. Không gửi dữ liệu thô.

Luật đề xuất cho MVP (đơn giản, giải thích được):

```
Xét theo thứ tự, gặp điều kiện nào trước thì lấy kết quả đó:
1. asleep  nếu: đang bật Không làm phiền / chế độ ngủ (getCurrentInterruptionFilter ≠ ALL)
              hoặc người dùng đã bấm "Thôi, ngủ luôn"/"Tối nay không xem" cho trận này
2. busy    nếu: giờ địa phương nằm trong "giờ đi làm" người dùng tự đặt (vd. fan ở Nhật 08:00–18:00),
              kể cả khi màn hình đang bật → để hết giờ làm mới báo
3. awake   nếu: màn hình đang bật (PowerManager.isInteractive)
              hoặc đã "điểm danh" trận này ("Đêm nay xem") và có dùng máy trong 15 phút gần nhất
4. còn lại → asleep (thà không gọi còn hơn đánh thức)
```

- Người `asleep`/`busy`: **không rung, không kêu**. Gửi một thông báo im lặng vào sáng
  (giờ báo thức kế tiếp, mặc định 07:00): "Đêm qua MU thua 0-2. Hang đang mở, Tuấn và Hùng đang trong hang."
  (người đã ở trong hang: "…có 2 cái vỗ vai cho bạn."). Vào hay không vẫn là người dùng tự chọn.
- Danh sách "cũng đang thức" chỉ hiện người `awake` trong **cùng hội**.
- Kiểm tra trên ≥ 3 dòng máy (Pixel, Samsung, Xiaomi) vì FCM im lặng có thể bị trễ khi máy ở chế độ Doze;
  dùng FCM ưu tiên cao, và khi người dùng mở app thì app tự kéo trạng thái hang.

### 3.3 Hang, vào/ra, sáng/tắt đèn (SRV + AND-1)

Mô hình dữ liệu:

```
Cave        {group_id, status: OPEN|CLOSED, opened_by_fixture, closed_by_fixture}
Membership  {cave_id, user_id, entered_at, left_at, left_reason: self|win}
LightWindow {cave_id, fixture_id, starts_at (= FT), ends_at (= FT + 30')}
```

- **Vào hang – tự chọn.** Khi hang sáng đèn, người **ở ngoài** và đang thức nhận thông báo
  "MU thua 0-2. Hang đang mở. Tuấn, Hùng đang trong hang." + **Vào hang** / **Thôi, ngủ luôn**.
  Bỏ qua thông báo = không vào; không nhắc lại trong đêm đó. Khi hang mở, trong app lúc nào cũng có nút **Vào hang**
  (kể cả ngoài giờ sáng đèn).
- Người **ở trong** và đang thức nhận "Hang sáng đèn" khi trận không thắng kết thúc; có nút
  **Rủ anh em vào hang** → mời những người ở ngoài, đang thức trong hội (mỗi người tối đa 1 lời mời mỗi trận).
- **Ra hang – tự chọn.** Nút **Ra khỏi hang** lúc nào cũng có; ra lặng lẽ, không báo ai, không ai thấy ai đã ra
  (đúng luật "không điểm danh"). Ra rồi muốn vào lại vẫn được khi hang còn mở.
- **Tắt đèn:** hết `LightWindow` (30 phút sau còi) thì hang im lặng: không vỗ vai, không thông báo,
  màn hang chỉ còn chế độ xem. Màn tắt đèn tính theo báo thức từng người (`AlarmManager.getNextAlarmClock()`):
  "Báo thức của bạn: 6:30. Còn kịp ngủ 2 tiếng." Không có báo thức thì bỏ dòng này.
- **Đóng hang khi MU thắng:** đóng `Cave`, mọi `Membership` còn mở được ghi `left_reason = win`, gửi
  "RA KHỎI HANG!" + nút "Ra hang ăn mừng" **chỉ tới người đang trong hang** (người ngủ/đang làm → dời tới sáng).
  Người ở ngoài hang không nhận gì.
- Màn hang bật `FLAG_SECURE` (chặn chụp màn hình), không có nút chia sẻ.

### 3.4 Câu vỗ vai do AI soạn (AI)

**Cách tạo** – một lần mỗi trận, ở máy chủ, không theo từng người:

1. Lúc hết trận, `PatService` gom **dữ kiện đã kiểm chứng** từ API: tỷ số, đối thủ, giải, chuỗi thua/không thắng,
   thứ hạng hiện tại, vài cột mốc lịch sử do CONTENT soạn sẵn (vd. "mùa 24-25 hạng 15").
2. Gọi Claude (mặc định `claude-opus-5-5`, đổi bằng `PAT_MODEL`) sinh ~12 câu.
   Prompt bắt buộc: **tự trào về MU**, mỗi câu có **đồng cảm + một góc nhìn mới**, ≤ 120 ký tự, giọng fan Việt,
   **chỉ dùng dữ kiện được cung cấp** (không tự bịa số liệu).
3. **Bộ lọc** (không qua được thì loại câu):
   - Không chứa tên cầu thủ/HLV/trọng tài của trận (danh sách lấy từ API), không nhắc fan đội khác.
   - Không chê/đổ lỗi (từ điển + một lượt Claude chấm "câu này có chê ai không?").
   - Số liệu trong câu phải khớp dữ kiện đầu vào.
4. Chọn 8 câu tốt nhất; **thiếu thì bù từ kho dự phòng** (~40 câu CONTENT viết sẵn, có nhãn theo kịch bản:
   thua 1 bàn, thua đậm, thua phút cuối, chuỗi thua, **hoà khi hang đang mở** – "vẫn chưa ra được hang",
   ở trong hang nhiều ngày…). API lỗi → dùng 100% kho dự phòng.
5. Với API miễn phí không theo dõi diễn biến được, nên câu được soạn **ngay khi biết kết quả, trước khi gửi thông báo**
   (giới hạn 60 giây; quá thời gian thì dùng kho dự phòng). Ai mở hang cũng thấy ngay câu vỗ vai.

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

Sự kiện telemetry (ID ẩn danh): `cave_opened, cave_closed, lights_on, invite_shown, invite_sent,
cave_enter{source: notification|invite|app}, cave_leave{reason: self|win}, pat_sent, pat_received,
lights_out_shown, screen_off_after_ft, exit_celebration_opened, survey_answer`.

| Chỉ số | Cách đo |
|---|---|
| % lần sáng đèn có ≥ 1 cái vỗ vai (proposal) | `LightWindow` có ≥ 1 `pat_sent` / số `LightWindow` có ≥ 2 người trong hang |
| % tắt máy trong 30 phút sau còi (proposal) | Trong giờ sáng đèn, app chạy dịch vụ nền ngắn (có thông báo "Hang đang sáng đèn") nghe sự kiện màn hình tắt; tính "tắt máy" = màn hình tắt trước FT + 30' và **không bật lại ≥ 20 phút** |
| "Đỡ cay một mình" sau 1 tháng (proposal) | Khảo sát 3 câu (thang 1–5) sau đêm pilot và sau 1 tháng |
| % người được mời chọn vào hang (thêm) | `cave_enter` / `invite_shown` – cho biết "vào hang" có hấp dẫn không khi đã là tự chọn |
| Ra hang tự nguyện vs. được đưa ra khi thắng (thêm) | Tỷ lệ `cave_leave{self}` / `{win}`, thời gian ở trong hang |

Lưu ý: chỉ số 2 không có nhóm đối chứng → khảo sát thêm "bình thường sau trận thua bạn thức thêm bao lâu?" lúc onboarding
để có mốc so sánh thô.

---

## 4. Lịch 2 tuần

| Ngày | AND-1 | AND-2 | SRV | AI | CONTENT |
|---|---|---|---|---|---|
| **T5 22/10** | Khung app Compose, FCM | Thử API thức/ngủ trên 3 dòng máy | FastAPI, Postgres, Firebase, key API-Football | Nháp prompt, danh sách dữ kiện | Hướng dẫn giọng văn câu vỗ vai |
| **T6 23/10** | Hội anh em (UI) | PresenceProbe v0 | Hợp đồng API (OpenAPI), ghi dữ liệu trận Atlético | Bộ lọc tên người | 20 câu dự phòng đầu · **M0** |
| T7–CN 24–25/10 | Thông báo "Hang đang mở" + Vào hang / Thôi | Báo thức, giờ đi làm, DND | MatchStateMachine, CaveService (Cave, Membership, LightWindow) | Sinh câu từ dữ kiện thật | Cột mốc lịch sử MU đã kiểm chứng |
| T2 26/10 | Màn hang + vỗ vai + nút Ra khỏi hang | Thông báo im lặng buổi sáng | Groups, Presence API | Lượt chấm "có chê ai không" | Đủ 40 câu dự phòng |
| T3 27/10 | Tắt đèn | Dịch vụ nền đo tắt máy | Replay ×N | Sinh trước theo kịch bản | Chấm câu AI vòng 1 |
| **T4 28/10** | Đóng hang khi thắng + màn ăn mừng | Telemetry client | PatService tích hợp | Sửa prompt | **M1: replay chạy hết vòng lặp** |
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
  `LightWindow` 30', dòng "còn kịp ngủ X tiếng", chống trùng sự kiện hết trận,
  `next_cave_open` / `lights_on` (L; L→D→D vẫn mở; D khi đóng; W đóng; thua/thắng luân lưu; bỏ giao hữu),
  vào/ra/vào lại, MU thắng chỉ đưa ra người đang trong hang.
- **AI:** bộ test 20 trận thua cũ + 30 câu "bẫy" (prompt chứa tên cầu thủ, số liệu sai) → bộ lọc phải loại.
- **E2E:** replay trên thiết bị thật với 4 máy: A thức + vào hang, B thức + bấm "Thôi", C bật DND, D tắt màn hình →
  chỉ A, B được gọi; C, D nhận thông báo im lặng buổi sáng; sau 30' hang tắt đèn nhưng vẫn mở;
  A bấm Ra khỏi hang rồi vào lại được; trận hoà giả lập → hang sáng đèn lại; trận thắng giả lập →
  hang đóng, **chỉ người đang trong hang** nhận "Ra khỏi hang", B không nhận gì.

## 6. Rủi ro & phương án

| Rủi ro | Phương án |
|---|---|
| **MU thắng đêm 4/11, hoặc hoà khi hang đang đóng → không sáng đèn** (proposal gọi đây là "rủi ro lớn nhất") | Diễn tập bằng replay tối 3/11 với chính nhóm pilot để có dữ liệu vòng lặp; nếu MU thắng thật thì đo luồng "Ra khỏi hang" (cần hang đã mở từ diễn tập hoặc trận thua trước đó) |
| Hang mở nhiều ngày → than thở kéo dài | Chỉ cho vỗ vai trong 30' sáng đèn sau mỗi trận; ngoài lúc đó hang im lặng, không thông báo |
| Vào hang là tự chọn → ít người vào, hang vắng | Đo `cave_enter / invite_shown`; thông báo nêu tên anh em đang trong hang để kéo người vào |
| Đoán sai thức/ngủ → đánh thức người đang ngủ | Mặc định coi là ngủ; thông báo hang không kêu nếu không chắc; nút "Đừng gọi tôi đêm nay" |
| FCM im lặng trễ do Doze/OEM | FCM ưu tiên cao; hướng dẫn tắt tối ưu pin cho pilot; app tự kéo trạng thái khi mở |
| AI bịa số liệu hoặc lỡ chê cầu thủ | Chỉ dùng dữ kiện đầu vào, bộ lọc 2 lớp, kho dự phòng |
| API-Football trễ/lỗi lúc 4h sáng | Người trực + admin endpoint bắn sự kiện tay |
| 20 người chưa đủ để kết luận | Nói rõ trong báo cáo; xem pilot là kiểm chứng vòng lặp + tín hiệu sớm |

## 7. Câu hỏi mở (chốt trước 23/10)

Đã chốt 22/10:

- **Hoà:** hang giữ nguyên – đang mở (sau trận thua) thì vẫn mở và sáng đèn lại; thua → hoà → hoà vẫn mở (§3.1).
- **Hang chỉ đóng khi MU thắng**; 30 phút sau còi chỉ là **tắt đèn**, không đóng hang (§3.3).
- **Vào hang và ra hang là quyết định của người dùng**; MU thắng thì tự động đưa ra, và **chỉ người đang trong hang**
  nhận "Ra khỏi hang" (§3.3).
- **Màn hang chỉ hiện người đang trong hang**; không hiện người ở ngoài/đang ngủ (bỏ avatar "N" mờ trong mockup).

Còn mở:

- **Q5. Ngân sách.** Các khoản dự kiến cho pilot:

  | Khoản | Ghi chú |
  |---|---|
  | API dữ liệu trận | **Chốt 22/10: tạm dùng gói miễn phí** (football-data.org hoặc API-Football Free, §3.1). Nâng gói trả phí sau nếu thiếu giải/mùa |
  | Claude API | Chỉ vài lượt gọi mỗi trận (sinh câu + chấm), không theo từng người → chi phí rất nhỏ |
  | Firebase (FCM, Anonymous Auth) | FCM miễn phí; gói miễn phí đủ cho 20 người |
  | Máy chủ (Cloud Run hoặc VPS nhỏ) | Lưu lượng pilot rất thấp, có thể nằm trong hạn mức miễn phí của Cloud Run |

  **Credit "Trial credit for GenAI App Builder" (¥159.885, hết hạn 11/4/2027):** theo các thảo luận cộng đồng và
  một phản hồi của bộ phận hỗ trợ Google được trích lại, credit này chỉ áp dụng cho nhóm Vertex AI Search /
  Agent Builder, **không** áp dụng cho Gemini API, Claude trên Vertex, Cloud Run hay API-Football. Kiến trúc hiện tại
  không dùng dịch vụ nào trong nhóm đó, nên nhiều khả năng credit này **không giúp được**. Cần xác nhận trong
  Cloud Console → Billing → Credits → bấm vào credit để xem danh sách dịch vụ/SKU áp dụng. Không nên đổi kiến trúc
  chỉ để tiêu credit.

---

## 8. Trạng thái triển khai (22/10)

| Phần | Trạng thái | Kiểm tra |
|---|---|---|
| `server/` – canh trận (football-data.org / API-Football / nhập tay), luật hang, vào/ra, sáng/tắt đèn, rủ anh em, vỗ vai, câu AI + bộ lọc + kho dự phòng, replay, telemetry, admin | Xong bản đầu | 71 test pytest qua, chạy thử bằng uvicorn |
| `android/core/` – luật thức/ngủ, giờ báo sáng, dòng "còn kịp ngủ", nhắc trận đêm, đọc tin FCM, lời thông báo | Xong bản đầu | 24 test JUnit qua |
| `android/app/` – đăng ký, hội anh em, màn hang (FLAG_SECURE), ăn mừng, nhận FCM, báo sáng (WorkManager), đo tắt máy | Đã viết, **chưa build** | Môi trường cloud chặn tải Android SDK (`dl.google.com`) |
| Firebase project, `google-services.json`, khoá dịch vụ FCM cho máy chủ | Chưa làm | Cần người có tài khoản Google tạo |
| Token football-data.org / key API-Football | Chưa có | Đăng ký miễn phí, thử `/v1/admin/schedule/refresh` |
| Triển khai máy chủ (Cloud Run hoặc VPS) | Chưa làm | |

