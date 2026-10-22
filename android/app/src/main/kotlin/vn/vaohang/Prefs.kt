package vn.vaohang

import android.content.Context
import vn.vaohang.core.WorkHours
import java.time.Instant
import java.time.LocalTime

/** Lưu cục bộ trên máy. Không có gì ở đây được gửi đi ngoài token đăng nhập. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("vaohang", Context.MODE_PRIVATE)

    var token: String?
        get() = sp.getString("token", null)
        set(v) = sp.edit().putString("token", v).apply()

    var userId: Long
        get() = sp.getLong("user_id", 0)
        set(v) = sp.edit().putLong("user_id", v).apply()

    var nickname: String?
        get() = sp.getString("nickname", null)
        set(v) = sp.edit().putString("nickname", v).apply()

    var fcmToken: String?
        get() = sp.getString("fcm_token", null)
        set(v) = sp.edit().putString("fcm_token", v).apply()

    /** Đồng ý đo "tắt máy sau trận" (ẩn danh). */
    var measureConsent: Boolean
        get() = sp.getBoolean("measure_consent", false)
        set(v) = sp.edit().putBoolean("measure_consent", v).apply()

    var workHours: WorkHours?
        get() {
            val start = sp.getString("work_start", null) ?: return null
            val end = sp.getString("work_end", null) ?: return null
            return WorkHours(LocalTime.parse(start), LocalTime.parse(end))
        }
        set(v) = sp.edit().apply {
            if (v == null) remove("work_start").remove("work_end")
            else putString("work_start", v.start.toString()).putString("work_end", v.end.toString())
        }.apply()

    var lastInteraction: Instant?
        get() = sp.getLong("last_interaction", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli)
        set(v) = sp.edit().putLong("last_interaction", v?.toEpochMilli() ?: 0).apply()

    fun touch() {
        lastInteraction = Instant.now()
    }

    /** "Đêm nay xem" (true) / "Tối nay không xem" (false) cho một trận. */
    fun setTonight(fixtureId: Long, watching: Boolean) {
        sp.edit().putString("tonight", "$fixtureId:$watching").apply()
    }

    fun tonight(fixtureId: Long): Boolean? {
        val (id, watching) = sp.getString("tonight", null)?.split(":")?.takeIf { it.size == 2 } ?: return null
        return if (id.toLongOrNull() == fixtureId) watching.toBoolean() else null
    }

    /** "Tối nay không xem" trước trận hoặc "Thôi, ngủ luôn" sau còi. */
    fun declinedTonight(fixtureId: Long): Boolean = tonight(fixtureId) == false
}
