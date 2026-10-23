package vn.vaohang

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import vn.vaohang.core.Copy
import vn.vaohang.ui.MainActivity
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Nút gắn trên thông báo. */
data class NotificationAction(val title: String, val intent: PendingIntent)

object Notifications {
    const val EXTRA_CAVE_ID = "cave_id"
    const val EXTRA_SOURCE = "source"
    const val EXTRA_SCREEN = "screen" // cave | celebrate

    fun openCave(context: Context, caveId: Long, source: String, screen: String = "cave"): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_CAVE_ID, caveId)
            .putExtra(EXTRA_SOURCE, source)
            .putExtra(EXTRA_SCREEN, screen)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, (caveId * 10 + screen.length).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @SuppressLint("MissingPermission") // đã kiểm tra quyền ngay bên dưới
    fun show(
        context: Context,
        id: Int,
        channel: String,
        copy: Copy,
        tap: PendingIntent?,
        actions: List<NotificationAction> = emptyList(),
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_vaohang)
            .setColor(BRAND_COLOR)
            .setLargeIcon(largeIcon(context))
            .setContentTitle(copy.title)
            .setContentText(copy.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy.body))
            .setAutoCancel(true)
            .setContentIntent(tap)
        if (channel == VaoHangApp.CHANNEL_QUIET) builder.setSilent(true)
        actions.forEach { builder.addAction(0, it.title, it.intent) }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    /** Màu lửa cho icon nhỏ và nút trên thông báo. */
    const val BRAND_COLOR = 0xFFFF9F43.toInt()

    /** Logo tròn (người tối cổ nâng cốc trước cửa hang) hiện cạnh nội dung thông báo. */
    fun largeIcon(context: Context): android.graphics.Bitmap? =
        android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.ic_notification_large)

    /** Báo nhẹ vào giờ khác (sáng mai / hết giờ làm) qua WorkManager. */
    fun showLater(context: Context, at: Instant, id: Int, copy: Copy, caveId: Long, screen: String) {
        val delay = Duration.between(Instant.now(), at).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<DeferredNotificationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    "id" to id, "title" to copy.title, "body" to copy.body, "cave_id" to caveId, "screen" to screen,
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("deferred-$id", ExistingWorkPolicy.REPLACE, request)
    }
}

class DeferredNotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val caveId = inputData.getLong("cave_id", 0)
        val screen = inputData.getString("screen") ?: "cave"
        Notifications.show(
            applicationContext,
            inputData.getInt("id", 1),
            VaoHangApp.CHANNEL_QUIET,
            Copy(inputData.getString("title").orEmpty(), inputData.getString("body").orEmpty()),
            Notifications.openCave(applicationContext, caveId, "morning", screen),
        )
        return Result.success()
    }
}
