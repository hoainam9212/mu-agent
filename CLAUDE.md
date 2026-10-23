# Vào Hang – ghi chú cho Claude

- Trả lời chủ dự án bằng **tiếng Việt**.
- Nhánh làm việc duy nhất: `claude/exciting-ramanujan-mptsqi`. Repo công khai: **không commit bí mật**
  (`google-services.json`, chuỗi database, token, khoá service account…).
- Kế hoạch sản phẩm và luật hang: `docs/PLAN.md`. Máy chủ: `server/` (FastAPI, chạy Cloud Run Tokyo,
  database Supabase). App: `android/` (Compose; APK build trên GitHub Actions).
- **Đọc trước tiên: `docs/HANDOFF.md`** – tình trạng mới nhất, việc đang dở, lệnh chạy thử, cách làm việc với chủ dự án.
- Hình ảnh cho app (người tối cổ mặc áo MU quanh đống lửa trong hang). Quy trình tạo ảnh bằng Gemini,
  danh sách ảnh, đoạn mô tả, cách kiểm tra và cách ghép vào app: **`docs/ART_HANDOFF.md`**.
- Xem trước giao diện không cần điện thoại: workflow **UI preview** (Paparazzi) lưu ảnh chụp vào `docs/ui-preview/`.
- Kiểm tra trước khi đẩy: `cd server && pytest -q`; `cd android && ./gradlew -p core test`.
