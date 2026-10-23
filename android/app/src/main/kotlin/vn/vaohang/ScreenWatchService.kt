package vn.vaohang

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Đo chỉ số "tắt máy trong 30 phút sau còi": màn hình tắt trước FT + 30' và không bật lại ít nhất 20'.
 * Chỉ chạy khi người dùng đã đồng ý, tối đa 50 phút, chỉ gửi lên kết quả có/không và số phút.
 */
class ScreenWatchService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var fullTime: Instant = Instant.now()
    private var offSince: Instant? = null
    private var reported = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> offSince = Instant.now()
                Intent.ACTION_SCREEN_ON -> offSince = null
            }
        }
    }

    private val check = object : Runnable {
        override fun run() {
            val now = Instant.now()
            val off = offSince
            when {
                off != null && off <= fullTime.plus(WINDOW) && Duration.between(off, now) >= STAY_OFF ->
                    finish(true, Duration.between(fullTime, off).toMinutes())
                now >= fullTime.plus(WINDOW).plus(STAY_OFF) -> finish(false, null)
                else -> handler.postDelayed(this, 60_000)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, VaoHangApp.CHANNEL_MEASURE)
            .setSmallIcon(R.drawable.ic_stat_vaohang)
            .setColor(Notifications.BRAND_COLOR)
            .setContentTitle("Ngủ ngon nhé 🌙")
            .setContentText("Đặt máy xuống là xong. Hang chỉ đếm giờ tắt máy, không ghi tên.")
            .setSilent(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        if (!reported && offSince == null) {
            fullTime = Instant.now()
            ContextCompat.registerReceiver(
                this, receiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            handler.postDelayed(check, 60_000)
        }
        return START_NOT_STICKY
    }

    private fun finish(turnedOff: Boolean, minutesAfterFullTime: Long?) {
        if (reported) return
        reported = true
        val app = application as VaoHangApp
        val props = buildMap {
            put("turned_off", if (turnedOff) "1" else "0")
            minutesAfterFullTime?.let { put("minutes_after_ft", "$it") }
        }
        app.scope.launch { app.api.telemetry("screen_off_after_ft", props) }
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(check)
        runCatching { unregisterReceiver(receiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 42
        private val WINDOW: Duration = Duration.ofMinutes(30)
        private val STAY_OFF: Duration = Duration.ofMinutes(20)

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ScreenWatchService::class.java)) }
        }
    }
}
