package vn.vaohang.core

import java.time.ZoneId

data class Copy(val title: String, val body: String)

/** Lời thông báo. Chỉ nhắc tên người đang trong hang, không điểm danh ai ở ngoài. */
object NotificationCopy {
    fun names(list: List<String>): String = when (list.size) {
        0 -> ""
        1 -> list[0]
        else -> list.dropLast(1).joinToString(", ") + " và " + list.last()
    }

    fun lightsOn(m: PushMessage.LightsOn, deferred: Boolean): Copy {
        val when_ = if (deferred) "Đêm qua " else ""
        if (m.youInside) {
            return if (deferred) {
                Copy("Bạn vẫn đang trong hang", "$when_${m.score}. Hang còn mở cho tới khi MU thắng.")
            } else {
                Copy(
                    if (m.result == "D") "Hang nhóm lửa lại" else "Hang đã nhóm lửa",
                    "${m.score}. Anh em ngồi quanh lửa vỗ vai nhau 30 phút.",
                )
            }
        }
        val title = if (m.result == "D") "Vẫn chưa ra được hang" else "Hang đang mở"
        val who = if (m.insideNames.isEmpty()) "Chưa ai vào hang." else "${names(m.insideNames)} đang trong hang."
        return Copy(title, "$when_${m.score}. $who")
    }

    fun exit(m: PushMessage.Exit, deferred: Boolean): Copy =
        Copy("RA KHỎI HANG!", "${if (deferred) "Đêm qua " else ""}${m.score}. MU thắng rồi, cả hội ra hang cùng lúc.")

    fun invite(m: PushMessage.Invite): Copy =
        Copy("${m.fromName} rủ bạn vào hang", "Anh em đang ngồi trong hang. Vào cùng không?")

    fun pat(m: PushMessage.PatReceived): Copy = Copy("${m.fromName} vỗ vai", m.text)

    fun reminder(m: PushMessage.MatchReminder, zone: ZoneId): Copy {
        val t = m.kickoff.atZone(zone).toLocalTime()
        val hhmm = "%02d:%02d".format(t.hour, t.minute)
        return Copy("Đêm nay MU gặp ${m.opponent}", "Bóng lăn lúc $hhmm. Đêm nay bạn xem không?")
    }
}
