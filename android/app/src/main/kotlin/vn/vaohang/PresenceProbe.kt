package vn.vaohang

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import vn.vaohang.core.Presence
import vn.vaohang.core.PresenceRules
import vn.vaohang.core.PresenceSignals
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

/** Đọc tín hiệu thức/ngủ trên máy. Chỉ kết quả (awake/asleep/busy) được gửi lên máy chủ. */
class PresenceProbe(private val context: Context, private val prefs: Prefs) {

    fun evaluate(fixtureId: Long?): Presence {
        val nm = context.getSystemService(NotificationManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        val filter = nm.currentInterruptionFilter
        val dnd = filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        val tonight = fixtureId?.let { prefs.tonight(it) }
        val signals = PresenceSignals(
            doNotDisturb = dnd,
            screenOn = pm.isInteractive,
            declinedTonight = tonight == false,
            checkedInTonight = tonight == true,
            sinceLastInteraction = prefs.lastInteraction?.let { Duration.between(it, Instant.now()) },
            localTime = LocalTime.now(),
            workHours = prefs.workHours,
        )
        return PresenceRules.evaluate(signals)
    }

    fun nextAlarm(): Instant? =
        context.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime?.let(Instant::ofEpochMilli)
}
