package vn.vaohang.core

import java.time.ZoneId

data class Copy(val title: String, val body: String)

/** Lời thông báo, giọng anh em trong hang. Chỉ nhắc tên người đang trong hang, không điểm danh ai ở ngoài. */
object NotificationCopy {
    fun names(list: List<String>): String = when (list.size) {
        0 -> ""
        1 -> list[0]
        else -> list.dropLast(1).joinToString(", ") + " và " + list.last()
    }

    fun lightsOn(m: PushMessage.LightsOn, deferred: Boolean): Copy {
        val draw = m.result == "D"
        if (m.youInside) {
            return if (deferred) {
                Copy("Anh em vẫn giữ lửa cho bạn", "Đêm qua ${m.score}. Hang còn đó, trận sau tính tiếp.")
            } else if (draw) {
                Copy("🔥 Hoà rồi, nhóm lửa lại thôi", "${m.score}. Chưa ra được hang thì Zô thêm cốc nữa.")
            } else {
                Copy("🔥 Lửa nhóm rồi, anh em ơi", "${m.score}. Ngồi xuống đây, Zô một cốc cho đỡ cay.")
            }
        }
        val title = if (draw) "Hoà… vẫn chưa ra được hang" else "Hang mở cửa rồi"
        val who = if (m.insideNames.isEmpty()) {
            "Lửa vừa nhóm, chưa ai vào. Bạn vào trước nhé?"
        } else {
            "${names(m.insideNames)} đang ngồi bên lửa. Vào Zô một cốc không?"
        }
        return Copy(title, "${if (deferred) "Đêm qua " else ""}${m.score}. $who")
    }

    fun exit(m: PushMessage.Exit, deferred: Boolean): Copy =
        if (deferred) Copy("RA KHỎI HANG! 🎉", "Đêm qua ${m.score}. MU thắng, hang đóng, anh em ra ánh sáng rồi.")
        else Copy("RA KHỎI HANG! 🎉", "${m.score}. MU thắng rồi! Cả hội lên đường ra ánh sáng.")

    fun invite(m: PushMessage.Invite): Copy =
        Copy("${m.fromName} gọi: vào hang Zô cái!", "Lửa đang cháy, cả hội đang chờ bạn.")

    fun pat(m: PushMessage.PatReceived): Copy = Copy("${m.fromName} nâng cốc với bạn 🍺", m.text)

    fun reminder(m: PushMessage.MatchReminder, zone: ZoneId): Copy {
        val t = m.kickoff.atZone(zone).toLocalTime()
        val hhmm = "%02d:%02d".format(t.hour, t.minute)
        return Copy("Đêm nay MU đá với ${m.opponent} 🌙", "Bóng lăn lúc $hhmm. Thức xem cùng anh em, hay ngủ trước để hang báo sau?")
    }
}
