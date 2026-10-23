# Bàn giao giữa các phiên (cập nhật 23/10/2026)

## Tình trạng
- App và máy chủ chạy được. Icon mới (người tối cổ nâng cốc "Zô") và icon thông báo đã đồng bộ.
- Câu chữ thông báo đã được chủ dự án duyệt (commit 8059754, `android/core/.../NotificationCopy.kt`).
- APK: workflow **CI** trên nhánh `claude/exciting-ramanujan-mptsqi`, artifact `vaohang-debug-apk`
  (khoá debug cố định, cài đè được).

## Đang làm: chạy thử với hội 3 (tên "test")
- Hội 3 đã bật `is_demo: true`. Đã replay `atletico-2026-10-13` → fixture 5, hang 3 đang **OPEN**.
- Thông báo không hiện ngay vì app hoãn khi màn hình tắt / giờ làm / Không làm phiền
  (`PresenceRules`, `DeliveryRules`). Đây là đúng thiết kế, không phải lỗi.
- Bước tiếp: vào hang trong app → `draw-demo` → `win-demo`. Giữ điện thoại sáng màn hình khi chạy.
- **Trước 25/10 23:00 giờ Tokyo (MU–Bournemouth):** đặt hội 3 (và hội 2 nếu còn demo) về `is_demo: false`.
- Trận thật kế tiếp sau đó: MU–Roma 3/11/2026 20:00 GMT (pilot).

## Lệnh cho người trực (chạy trong Cloud Shell)
Token lấy thẳng từ Cloud Run, không dán tay, không in ra:
```bash
API=https://vaohang-673963989819.asia-northeast1.run.app
ADMIN_TOKEN=$(gcloud run services describe vaohang --region asia-northeast1 --format=export | grep -A1 'name: ADMIN_TOKEN' | sed -n 's/ *value: //p')
H="X-Admin-Token: $ADMIN_TOKEN"
curl -s -H "$H" -H 'content-type: application/json' -X POST $API/v1/admin/groups/3/demo -d '{"is_demo":true}'; echo
curl -s -H "$H" -H 'content-type: application/json' -X POST $API/v1/admin/replay -d '{"scenario":"win-demo","speed":95}'; echo
curl -s -H "$H" -X POST $API/v1/admin/tick; echo
curl -s -H "$H" $API/v1/admin/status; echo
```
Kịch bản replay: `atletico-2026-10-13` (thua), `draw-demo`, `win-demo`. Hội demo chỉ nhận trận replay.
Không chạy lại `deploy/cloudrun.sh` chỉ để lấy token (nó sinh token mới).

Kiểm tra nhanh trong Supabase SQL Editor (không lộ mã FCM):
```sql
select 'fixture' t, id::text, status a, result b, coalesce(processed_at::text,'chưa xử lý') c
  from (select * from fixtures order by id desc limit 3) f
union all
select 'cave', id::text, status, opened_at::text, null from caves where group_id = 3
union all
select 'member', u.id::text || ' ' || u.nickname, gm.status, 'fcm_len=' || coalesce(length(u.fcm_token),0)::text, null
  from group_members gm join users u on u.id = gm.user_id where gm.group_id = 3;
```

## Cách làm việc với chủ dự án
- Câu chữ hiển thị cho người dùng (thông báo, nút, màn hình): **đưa bảng cũ → mới để duyệt trước**, chỉ commit/đẩy sau khi đồng ý.
- Không bảo chủ dự án dán token/bí mật vào chat. Phiên cloud không có quyền gcloud hay token admin:
  đưa lệnh để chủ dự án tự chạy trong Cloud Shell / Supabase rồi đọc kết quả họ gửi lại.
- Tránh câu khô cứng, dài dòng; giọng anh em, tự nhiên. Không nêu tên ai đang ở trong hang với người ở ngoài.
