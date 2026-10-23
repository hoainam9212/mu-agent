# Bàn giao: tạo ảnh cho Vào Hang bằng Gemini (Claude Desktop + Claude in Chrome)

Tài liệu này dành cho phiên Claude chạy trên Claude Desktop, điều khiển trình duyệt Chrome của chủ dự án
(extension Claude in Chrome) để tạo ảnh trên **https://gemini.google.com**. Đọc hết trước khi làm.
Trả lời chủ dự án bằng **tiếng Việt**.

## 1. Bối cảnh

- **Vào Hang**: app Android cho fan Manchester United người Việt. MU thua thì một "hang" mở cho hội anh em;
  cả hội là **người tối cổ mặc áo đỏ MU ngồi quanh đống lửa**, vỗ vai nhau bằng câu soạn sẵn; 30 phút sau
  **lửa tắt**, cả hội lăn ra ngủ; MU thắng thì cả hội nhảy mừng chạy ra khỏi hang.
- Mã nguồn: repo `hoainam9212/mu-agent`, nhánh **`claude/exciting-ramanujan-mptsqi`** (nhánh duy nhất).
- App ghép cảnh từ **từng lớp ảnh rời** (không chèn nguyên một bức tranh):
  nền hang (sáng / tối / lối ra) + đống lửa (cháy / tắt) + 6 kiểu nhân vật × nhiều tư thế, đặt bằng code.
  Vì vậy nhân vật và lửa phải nằm trên **nền xanh lá thuần** để tách nền; nền hang thì **không có người, không có lửa**.
- Bộ ảnh đất nặn (claymation) đã có trong app. Phong cách đang thử thay thế: **chibi 2D** (xem mục 3).
  **Hỏi chủ dự án chọn phong cách nào trước khi tạo cả bộ** (mục 2).

## 2. Việc cần làm trong phiên này

1. Hỏi chủ dự án: tạo bộ **chibi 2D** (mặc định, mục 3–4) hay chỉ bổ sung ảnh còn thiếu của bộ **đất nặn** (mục 6).
2. Mở gemini.google.com trong Chrome (chủ dự án đã đăng nhập sẵn; **không** đăng nhập hộ, không nhập mật khẩu).
   Tạo **tất cả ảnh trong cùng một cuộc trò chuyện Gemini** để giữ đồng bộ phong cách và nhân vật.
3. Với mỗi ảnh: dán đúng đoạn mô tả ở mục 4, chờ ảnh xong, **tự kiểm tra theo danh sách ở mục 5**;
   không đạt thì bảo Gemini vẽ lại (nói rõ sai chỗ nào), tối đa 3 lần rồi báo chủ dự án.
4. Tải ảnh **độ phân giải cao nhất** (nút tải xuống của Gemini), đổi tên đúng như bảng ở mục 4,
   để chung một thư mục, ví dụ `~/Downloads/vaohang-art/`.
5. Nộp ảnh (mục 7) và báo lại chủ dự án: ảnh nào đạt, ảnh nào phải vẽ lại mấy lần, còn thiếu gì.

Không làm: không bấm quảng cáo, không mở trang khác ngoài Gemini (trừ khi chủ dự án bảo), không đăng
ảnh lên mạng, không dùng logo thật của câu lạc bộ.

## 3. Hướng phong cách chibi 2D (đã thống nhất)

Ảnh thử đầu tiên chủ dự án tạo (5 bạn ngồi quanh lửa, một bạn đặt tay lên vai bạn bên cạnh) **đúng không khí**:
hang tối xanh, lửa nhỏ ấm, êm dịu, không chói. Phải sửa 3 điểm:

1. Nhân vật là **người lớn kiểu chibi** (đầu to, mắt to nhưng có râu ria, lông mày rậm, bụng bia, tóc bù xù),
   **không phải trẻ con**.
2. **6 người khác hẳn nhau** (tóc, râu, dáng), giữ **đúng thứ tự** ở mọi tư thế.
3. Có chất người tối cổ: **khố da báo**, chân trần; áo **đỏ, cổ bẻ trắng**. Bóng là **bóng đá tròn đen trắng**
   (viết `soccer`, đừng viết `football` – Gemini sẽ vẽ bóng bầu dục).

Không khí chung: đêm, ấm, buồn nhưng có anh em; **không neon, không hiệu ứng chói** (app mở lúc 3–4 giờ sáng).

## 4. Danh sách ảnh và đoạn mô tả (chibi 2D)

Dán lần lượt, đúng thứ tự (ảnh 1 định nhân vật; các ảnh sau bám theo).

| # | Tên file khi lưu | Nội dung |
|---|---|---|
| 1 | `chars_sit.png` | 6 nhân vật ngồi xếp bằng |
| 2 | `chars_pat.png` | 6 nhân vật ngồi, với tay vỗ vai bạn bên phải |
| 3 | `chars_sleep.png` | 6 nhân vật nằm ngang ngủ trên đất |
| 4 | `chars_cheer.png` | 6 nhân vật nhảy mừng |
| 5 | `fire_burning.png` | đống lửa đang cháy |
| 6 | `fire_out.png` | đống lửa đã tắt |
| 7 | `bg_lit.png` | nền hang trống, có vệt sáng lửa trên nền đất |
| 8 | `bg_dark.png` | cùng nền hang, bản tối |
| 9 | `bg_exit.png` | lối ra hang có nắng sáng |

**1. chars_sit.png**
```
Chibi 2D cartoon style with thick dark outlines and soft cel shading, cozy night mood. Character sheet of 6 DIFFERENT adult Vietnamese cavemen, chibi proportions (big heads, big expressive eyes) but clearly grown men: 1) big bushy beard, 2) thin mustache and beer belly, 3) messy long hair, 4) bald top with side hair and stubble, 5) young with spiky hair, 6) chubby with a short beard. All wear the same red soccer jersey with a white folded collar and a leopard-skin loincloth, barefoot. Each sits cross-legged facing the camera with a sad but warm expression. Arrange them in a 3x2 grid (3 columns, 2 rows) with lots of empty space between them, all the same size. Solid flat pure green background (#00FF00), no floor, no shadows, no text, no logos.
```

**2. chars_pat.png**
```
The same 6 cavemen, same order, same 3x2 grid, same size. Each one sits cross-legged and reaches one arm out to his right side as if patting a friend's shoulder, with a gentle comforting smile. Solid flat pure green background (#00FF00), no floor, no shadows, no text, no logos.
```

**3. chars_sleep.png**
```
The same 6 cavemen, same order, same 3x2 grid. Each one is lying flat horizontally on his side, asleep with eyes closed, as if lying on the ground seen from slightly above; one hugs a round black-and-white soccer ball, one uses a wooden club as a pillow, one snores with his mouth open. Every figure must be horizontal (head on one side, feet on the other). Solid flat pure green background (#00FF00), no floor, no shadows, no text, no logos.
```

**4. chars_cheer.png**
```
The same 6 cavemen, same order, same 3x2 grid. Each one jumps with both arms raised, cheering with a huge happy smile. Solid flat pure green background (#00FF00), no floor, no shadows, no text, no logos.
```

**5. fire_burning.png**
```
In the same chibi 2D style: a small campfire, crossed wooden logs inside a ring of small stones, warm orange and yellow flames, a few sparks, seen from a slightly-above angle, centered. Solid flat pure green background (#00FF00), no floor, no shadows, no text.
```

**6. fire_out.png**
```
The same campfire, same angle, same size and position, but burnt out: charred black logs, a few glowing red embers, a thin wisp of grey smoke. Solid flat pure green background (#00FF00), no floor, no shadows, no text.
```

**7. bg_lit.png**
```
In the same chibi 2D style: an empty cozy cave interior at night, no characters, no fire. A wide, flat, open dirt floor fills the whole lower half of the image, with a warm orange glow on the center of the floor as if a campfire was there. Dark blue-brown cave walls on the sides, a few stalactites on the ceiling, an opening at the back showing the night sky. Keep the floor clear of rocks in the middle. Vertical 9:16, no text.
```

**8. bg_dark.png**
```
Exactly the same empty cave, same framing and camera angle, but darker: no warm glow, the cave lit only by soft blue moonlight from the opening. No characters, no fire. Vertical 9:16, no text.
```

**9. bg_exit.png**
```
Same style: the mouth of the same cave seen from inside, bright warm morning sunlight pouring in from the opening, green hills outside, the dirt floor leading out. No characters, no text. Vertical 9:16.
```

## 5. Kiểm tra từng ảnh trước khi lưu

Nhân vật (ảnh 1–4):
- [ ] Đúng **6** người, lưới **3 cột × 2 hàng**, mỗi người **tách rời** (tay, chân không chạm người khác hay mép ô).
- [ ] 6 người **khác nhau rõ**, và **cùng thứ tự** với ảnh 1 (so mặt từng ô).
- [ ] Người lớn chibi (có râu ria…), áo đỏ cổ bẻ trắng, khố da báo, chân trần.
- [ ] Nền **xanh lá thuần đều màu**: không sàn, không bóng đổ, không vật thể lạ, không chữ.
- [ ] Ảnh 3: **tất cả nằm ngang**. Ảnh 2: tay với sang **bên phải** người đó.

Lửa (ảnh 5–6): nền xanh thuần, cùng góc nhìn và vị trí đống lửa giữa hai ảnh.

Nền (ảnh 7–9): **không người, không lửa**; ảnh 7–8 **cùng khung hình** (đặt cạnh nhau phải trùng vách, trùng nhũ đá);
nửa dưới là **nền đất rộng, phẳng, trống** (chỗ cho cả hội ngồi).

Nếu ảnh có dấu mờ của Gemini ở góc, vẫn lưu; báo lại để xử lý khi ghép.

## 6. Nếu chủ dự án giữ phong cách đất nặn

Bộ đất nặn đã có trong app (nền sáng/tối, lửa, 6 nhân vật × ngồi/ngủ/nhảy). Chỉ còn thiếu:
- `bg_exit.png` – lối ra hang đất nặn có nắng (ảnh 9, đổi `chibi 2D style` thành `claymation style`).
- Tuỳ chọn: `chars_pat.png` đất nặn (ảnh 2, đổi phong cách tương tự) để có cử chỉ vỗ vai.

## 7. Nộp ảnh

- Nếu máy có sẵn bản clone repo: chép ảnh vào `art/incoming/<chibi|clay>/` đúng tên ở mục 4, rồi
  `git add`, `git commit -m "Art: <phong cách> assets from Gemini"`, `git push origin claude/exciting-ramanujan-mptsqi`.
- Nếu không: báo chủ dự án thư mục chứa ảnh, để họ tải ảnh lên phiên Claude Code đang làm app.

## 8. Phần ghép vào app (phiên Claude Code làm, ghi lại để không phải dò lại)

**Trạng thái (23/10):** bộ chibi trong `art/incoming/chibi/` đã ghép vào app (thay bộ đất nặn). Nền 1536×2752,
vệt sáng lửa ở (0.50, 0.685); nền đất từ y ≈ 0.54. Ảnh vỗ vai: tay chỉ sang trái người xem, ô số 0 Gemini vẽ ngồi.

- Tách nền: `python3 -I android/tools/art/keyout.py sheet art/incoming/chibi/chars_sit.png sit --cols 3 --rows 2 --out android/app/src/main/res/drawable-nodpi`
  (tương tự `pat`, `sleep`, `cheer`; `fire fire_burning.png fire_out.png`; `bg bg_lit.png bg_dark.png bg_exit.png`).
  Bộ đất nặn cũ dùng lưới `--cols 2 --rows 3`.
- Code liên quan: `android/app/src/main/kotlin/vn/vaohang/ui/CaveArt.kt`
  - `GLOW_X`, `GLOW_Y`: vị trí vệt sáng lửa trên ảnh nền (đo lại cho nền mới).
  - `FLOOR`: đa giác nền đất ngồi được trên ảnh nền (đo lại bằng lưới; ngoài vùng này là vách hang).
  - `CAVE_W`, `CAVE_H`: kích thước ảnh nền.
  - `LyingWhenAsleep`: kiểu nhân vật nào ngủ nằm (với bộ chibi nằm ngang hết thì là cả 6, bỏ bước xoay ảnh).
  - `SitSprites` / `SleepSprites` / `CheerSprites`: thêm `PatSprites` nếu có ảnh vỗ vai.
- Xem trước giao diện: chạy workflow **UI preview** (Actions → UI preview → Run workflow); ảnh chụp lưu ở `docs/ui-preview/`.
