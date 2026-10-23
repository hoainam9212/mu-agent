package vn.vaohang.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import vn.vaohang.Notifications
import vn.vaohang.VaoHangApp

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        // Nền hang tràn ra sau thanh trạng thái; nội dung tự chừa chỗ (systemBarsPadding).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        (application as VaoHangApp).prefs.touch()
        handle(intent)
        setContent { VaoHangTheme { App(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val caveId = intent?.getLongExtra(Notifications.EXTRA_CAVE_ID, 0) ?: 0
        if (caveId <= 0) return
        vm.openFromNotification(
            caveId,
            intent?.getStringExtra(Notifications.EXTRA_SCREEN) ?: "cave",
            intent?.getStringExtra(Notifications.EXTRA_SOURCE) ?: "notification",
        )
    }
}
