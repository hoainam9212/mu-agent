# App Android Vào Hang

- `core/` – logic thuần Kotlin, không cần Android SDK: luật thức/ngủ, giờ báo nhẹ (báo thức / 07:00 / hết giờ làm),
  dòng "Còn kịp ngủ 2 tiếng", nhắc trận đêm, đọc FCM data message, lời thông báo tiếng Việt.
- `app/` – Jetpack Compose: đăng ký + màn đồng ý, hội anh em (mã mời, duyệt), màn hang (FLAG_SECURE, chỉ hiện người
  đang trong hang, vỗ vai, rủ anh em, ra khỏi hang, tắt đèn), màn "RA KHỎI HANG!", nhận FCM (`PushService`),
  báo sáng qua WorkManager, đo "tắt máy sau còi" (`ScreenWatchService`, chỉ khi người dùng đồng ý).

## Build

```bash
cd android
./gradlew -p core test               # test logic (24 test), không cần Android SDK
./gradlew assembleDebug -Pvaohang.serverUrl=https://<máy-chủ>
```

Cần Android SDK (compileSdk 35) và JDK 17+. Muốn nhận thông báo đẩy: tạo Firebase project, tải
`google-services.json` vào `app/` (file này không commit). Thiếu file này app vẫn mở được nhưng không nhận FCM.

Không có Android SDK thì để GitHub Actions build: mỗi lần push, workflow `CI` chạy test `core` và build APK debug,
tải về ở trang của lần chạy, mục Artifacts → `vaohang-debug-apk`.

Mặc định `vaohang.serverUrl=http://10.0.2.2:8000` (máy chủ chạy trên máy tính, app chạy trên emulator).
Chỉ địa chỉ này và `localhost` được dùng HTTP thường; pilot phải dùng HTTPS.
