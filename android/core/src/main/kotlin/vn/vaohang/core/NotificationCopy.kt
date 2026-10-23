package vn.vaohang.core

import java.time.ZoneId

data class Copy(val title: String, val body: String)

/** Lời thông báo, giọng anh em trong hang. Không nêu tên ai đang trong hay ngoài hang. */
object NotificationCopy {
    fun lightsOn(m: PushMessage.LightsOn, deferred: Boolean): Copy {
        val draw = m.result == "D"
        if (m.youInside) {
            return if (deferred) {
                Copy("Anh em vẫn giữ lửa cho bạn", "Đêm qua ${m.score}. Anh em vẫn ngồi đây, chờ MU gỡ lại.")
            } else if (draw) {
                Copy("🔥 Hoà rồi, nhóm lửa lại thôi", "${m.score}. Chưa ra được hang thì Zô thêm cốc nữa.")
            } else {
                Copy("🔥 Lửa nhóm rồi, anh em ơi", "${m.score}. Ngồi xuống đây, Zô một cốc cho đỡ cay.")
            }
        }
        val title = if (draw) "Hoà… vẫn chưa ra được hang" else "Muốn vào hang không?"
        return Copy(title, "${if (deferred) "Đêm qua " else ""}${m.score}. Lửa nhóm rồi, vào thôi anh em!")
    }

    fun exit(m: PushMessage.Exit, deferred: Boolean): Copy =
        if (deferred) Copy("RA KHỎI HANG! 🎉", "Đêm qua ${m.score}. MU thắng, anh em ra khỏi hang rồi!")
        else Copy("RA KHỎI HANG! 🎉", "${m.score}. MU thắng rồi, ra ngoài ăn mừng thôi anh em!")

    fun invite(m: PushMessage.Invite): Copy =
        Copy("${m.fromName} gọi: vào hang Zô cái!", "Anh em ngồi đủ cả rồi, thiếu mỗi bạn thôi.")

    fun pat(m: PushMessage.PatReceived): Copy = Copy("${m.fromName} nâng cốc với bạn 🍺", m.text)

    fun reminder(m: PushMessage.MatchReminder, zone: ZoneId): Copy {
        val t = m.kickoff.atZone(zone).toLocalTime()
        val hhmm = "%02d:%02d".format(t.hour, t.minute)
        return Copy("Đêm nay MU đá với ${m.opponent} 🌙", "$hhmm bóng lăn. Thức xem không bạn?")
    }
}
