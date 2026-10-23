package vn.vaohang

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VaoHangApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var api: Api
        private set
    val scope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        api = Api(BuildConfig.SERVER_URL, prefs)
        createChannels()
        refreshFcmToken()
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_CAVE, "Hang", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Hang mở, nhóm lửa, ra khỏi hang"
                },
                NotificationChannel(CHANNEL_PATS, "Vỗ vai", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_QUIET, "Tin buổi sáng", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Báo nhẹ cho người đã ngủ, không rung không kêu"
                },
                NotificationChannel(CHANNEL_REMINDER, "Nhắc trận đêm", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_MEASURE, "Đang đo sau trận", NotificationManager.IMPORTANCE_MIN),
            ),
        )
    }

    /** Firebase chỉ chạy khi đã có google-services.json; thiếu thì app vẫn mở được để demo qua máy chủ. */
    fun refreshFcmToken() {
        if (FirebaseApp.getApps(this).isEmpty() && FirebaseApp.initializeApp(this) == null) {
            Log.w(TAG, "Firebase chưa cấu hình – không nhận được thông báo đẩy")
            return
        }
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            prefs.fcmToken = token
            if (prefs.token != null) scope.launch { runCatching { api.updateFcmToken(token) } }
        }
    }

    companion object {
        const val TAG = "VaoHang"
        const val CHANNEL_CAVE = "cave"
        const val CHANNEL_PATS = "pats"
        const val CHANNEL_QUIET = "quiet"
        const val CHANNEL_REMINDER = "reminder"
        const val CHANNEL_MEASURE = "measure"
    }
}
