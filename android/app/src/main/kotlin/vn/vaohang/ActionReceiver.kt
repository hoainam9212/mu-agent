package vn.vaohang

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch

/** Nút "Đêm nay xem" / "Tối nay không xem" / "Thôi, ngủ luôn" trên thông báo. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TONIGHT) return
        val app = context.applicationContext as VaoHangApp
        val fixtureId = intent.getLongExtra(EXTRA_FIXTURE, 0)
        val watching = intent.getBooleanExtra(EXTRA_WATCHING, false)
        app.prefs.setTonight(fixtureId, watching)
        app.prefs.touch()
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
        val pending = goAsync()
        app.scope.launch {
            try {
                app.api.telemetry(if (watching) "checkin" else "not_tonight_tapped", mapOf("fixture_id" to "$fixtureId"))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_TONIGHT = "vn.vaohang.TONIGHT"
        private const val EXTRA_FIXTURE = "fixture_id"
        private const val EXTRA_WATCHING = "watching"
        private const val EXTRA_NOTIFICATION = "notification_id"

        fun tonight(context: Context, fixtureId: Long, watching: Boolean, notificationId: Int): PendingIntent {
            val intent = Intent(context, ActionReceiver::class.java)
                .setAction(ACTION_TONIGHT)
                .putExtra(EXTRA_FIXTURE, fixtureId)
                .putExtra(EXTRA_WATCHING, watching)
                .putExtra(EXTRA_NOTIFICATION, notificationId)
            return PendingIntent.getBroadcast(
                context, notificationId * 2 + if (watching) 1 else 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
