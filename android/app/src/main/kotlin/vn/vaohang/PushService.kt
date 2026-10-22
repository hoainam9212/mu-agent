package vn.vaohang

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import vn.vaohang.core.DeferredDelivery
import vn.vaohang.core.Delivery
import vn.vaohang.core.DeliveryRules
import vn.vaohang.core.NotificationCopy
import vn.vaohang.core.Presence
import vn.vaohang.core.PushMessage
import vn.vaohang.core.ReminderRule
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Máy chủ chỉ gửi data message. App tự quyết: đang thức thì báo ngay,
 * đang ngủ/đi làm thì báo nhẹ sau, lời mời và vỗ vai thì bỏ nếu không thức.
 */
class PushService : FirebaseMessagingService() {
    private val app get() = application as VaoHangApp

    override fun onNewToken(token: String) {
        app.prefs.fcmToken = token
        if (app.prefs.token != null) app.scope.launch { runCatching { app.api.updateFcmToken(token) } }
    }

    override fun onMessageReceived(remote: RemoteMessage) {
        val message = PushMessage.parse(remote.data) ?: return
        val probe = PresenceProbe(this, app.prefs)
        val fixtureId = (message as? PushMessage.LightsOn)?.fixtureId
        val presence = probe.evaluate(fixtureId)
        when (message) {
            is PushMessage.LightsOn -> onLightsOn(message, presence, probe)
            is PushMessage.Exit -> onExit(message, presence, probe)
            is PushMessage.Invite -> if (DeliveryRules.decide(message, presence) == Delivery.NOW) {
                Notifications.show(
                    this, notificationId(message.caveId, 2), VaoHangApp.CHANNEL_CAVE,
                    NotificationCopy.invite(message),
                    Notifications.openCave(this, message.caveId, "invite"),
                    listOf(NotificationAction("Vào hang", Notifications.openCave(this, message.caveId, "invite", "enter"))),
                )
            }
            is PushMessage.PatReceived -> if (DeliveryRules.decide(message, presence) == Delivery.NOW) {
                Notifications.show(
                    this, notificationId(message.caveId, 3), VaoHangApp.CHANNEL_PATS,
                    NotificationCopy.pat(message), Notifications.openCave(this, message.caveId, "pat"),
                )
            }
            is PushMessage.MatchReminder -> onReminder(message)
        }
    }

    private fun onLightsOn(m: PushMessage.LightsOn, presence: Presence, probe: PresenceProbe) {
        // Báo trạng thái để "Rủ anh em" chỉ gọi người đang thức. Không đợi quá 8 giây.
        runBlocking { withTimeoutOrNull(8_000) { runCatching { app.api.presence(m.fixtureId, presence.wire) } } }
        val id = notificationId(m.caveId, 1)
        when (DeliveryRules.decide(m, presence)) {
            Delivery.NOW -> {
                val actions = if (m.youInside) emptyList() else listOf(
                    NotificationAction("Vào hang", Notifications.openCave(this, m.caveId, "notification", "enter")),
                    NotificationAction("Thôi, ngủ luôn", ActionReceiver.tonight(this, m.fixtureId, false, id)),
                )
                Notifications.show(
                    this, id, VaoHangApp.CHANNEL_CAVE, NotificationCopy.lightsOn(m, deferred = false),
                    Notifications.openCave(this, m.caveId, "notification"), actions,
                )
                if (!m.youInside) app.scope.launch { app.api.telemetry("invite_shown", mapOf("cave_id" to "${m.caveId}")) }
                if (app.prefs.measureConsent) ScreenWatchService.start(this)
            }
            Delivery.DEFERRED -> Notifications.showLater(
                this, deferredAt(presence, probe), id, NotificationCopy.lightsOn(m, deferred = true), m.caveId, "cave",
            )
            Delivery.DROP -> Unit
        }
    }

    private fun onExit(m: PushMessage.Exit, presence: Presence, probe: PresenceProbe) {
        val id = notificationId(m.caveId, 4)
        when (DeliveryRules.decide(m, presence)) {
            Delivery.NOW -> Notifications.show(
                this, id, VaoHangApp.CHANNEL_CAVE, NotificationCopy.exit(m, deferred = false),
                Notifications.openCave(this, m.caveId, "exit", "celebrate"),
            )
            Delivery.DEFERRED -> Notifications.showLater(
                this, deferredAt(presence, probe), id, NotificationCopy.exit(m, deferred = true), m.caveId, "celebrate",
            )
            Delivery.DROP -> Unit
        }
    }

    private fun onReminder(m: PushMessage.MatchReminder) {
        val zone = ZoneId.systemDefault()
        if (!ReminderRule.isNightMatch(m.kickoff, zone)) return
        val id = 1_000_000 + m.fixtureId.toInt()
        Notifications.show(
            this, id, VaoHangApp.CHANNEL_REMINDER, NotificationCopy.reminder(m, zone), null,
            listOf(
                NotificationAction("Đêm nay xem", ActionReceiver.tonight(this, m.fixtureId, true, id)),
                NotificationAction("Tối nay không xem", ActionReceiver.tonight(this, m.fixtureId, false, id)),
            ),
        )
        app.scope.launch { app.api.telemetry("reminder_shown") }
    }

    private fun deferredAt(presence: Presence, probe: PresenceProbe) =
        DeferredDelivery.at(presence, ZonedDateTime.now(), probe.nextAlarm(), app.prefs.workHours)

    private fun notificationId(caveId: Long, kind: Int) = (caveId * 10 + kind).toInt()
}
