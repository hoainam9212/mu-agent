package vn.vaohang.core

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class Presence {
    AWAKE, ASLEEP, BUSY;

    /** Giá trị gửi lên máy chủ (`POST /v1/presence`). */
    val wire: String get() = name.lowercase()
}

/** Giờ đi làm người dùng tự đặt; có thể vắt qua nửa đêm. */
data class WorkHours(val start: LocalTime, val end: LocalTime) {
    fun contains(t: LocalTime): Boolean =
        if (start <= end) t >= start && t < end else t >= start || t < end
}

/** Tín hiệu đọc trên máy. Không có dữ liệu nào trong đây được gửi đi, chỉ gửi kết quả. */
data class PresenceSignals(
    val doNotDisturb: Boolean,
    val screenOn: Boolean,
    val declinedTonight: Boolean,
    val checkedInTonight: Boolean,
    val sinceLastInteraction: Duration?,
    val localTime: LocalTime,
    val workHours: WorkHours?,
)

object PresenceRules {
    val RECENT_INTERACTION: Duration = Duration.ofMinutes(15)

    /** Không chắc thì coi là đang ngủ: thà không gọi còn hơn đánh thức. */
    fun evaluate(s: PresenceSignals): Presence = when {
        s.doNotDisturb || s.declinedTonight -> Presence.ASLEEP
        s.workHours?.contains(s.localTime) == true -> Presence.BUSY
        s.screenOn -> Presence.AWAKE
        s.checkedInTonight && s.sinceLastInteraction != null && s.sinceLastInteraction <= RECENT_INTERACTION ->
            Presence.AWAKE
        else -> Presence.ASLEEP
    }
}

object DeferredDelivery {
    private val MAX_ALARM_AHEAD: Duration = Duration.ofHours(12)
    private val DEFAULT_MORNING: LocalTime = LocalTime.of(7, 0)

    /**
     * Thời điểm báo nhẹ cho người không thức: hết giờ làm (nếu đang bận),
     * hoặc giờ báo thức kế tiếp trong 12 tiếng tới, hoặc 07:00 sáng.
     */
    fun at(presence: Presence, now: ZonedDateTime, nextAlarm: Instant?, workHours: WorkHours?): Instant {
        if (presence == Presence.BUSY && workHours != null) {
            return nextOccurrence(now, workHours.end)
        }
        if (nextAlarm != null && nextAlarm.isAfter(now.toInstant()) &&
            Duration.between(now.toInstant(), nextAlarm) <= MAX_ALARM_AHEAD
        ) {
            return nextAlarm
        }
        return nextOccurrence(now, DEFAULT_MORNING)
    }

    private fun nextOccurrence(now: ZonedDateTime, time: LocalTime): Instant {
        var candidate = now.toLocalDate().atTime(time).atZone(now.zone)
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1)
        return candidate.toInstant()
    }
}

object ReminderRule {
    /** Chỉ nhắc các trận bóng lăn trong khoảng 00:00–05:59 theo giờ máy. */
    fun isNightMatch(kickoff: Instant, zone: ZoneId): Boolean = kickoff.atZone(zone).hour < 6
}

object LightsOut {
    private val MAX_AHEAD: Duration = Duration.ofHours(12)

    /** "Báo thức của bạn: 6:30.\nCòn kịp ngủ 2 tiếng." – null nếu không có báo thức gần. */
    fun text(now: Instant, nextAlarm: Instant?, zone: ZoneId): String? {
        if (nextAlarm == null || !nextAlarm.isAfter(now)) return null
        val left = Duration.between(now, nextAlarm)
        if (left > MAX_AHEAD) return null
        val alarm = nextAlarm.atZone(zone).toLocalTime()
        val alarmText = "${alarm.hour}:${alarm.minute.toString().padStart(2, '0')}"
        val minutes = left.toMinutes()
        val sleep = if (minutes >= 60) "${minutes / 60} tiếng" else "$minutes phút"
        return "Báo thức của bạn: $alarmText.\nCòn kịp ngủ $sleep."
    }
}
