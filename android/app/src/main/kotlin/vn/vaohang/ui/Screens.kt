package vn.vaohang.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.vaohang.CaveView
import vn.vaohang.GroupDto
import vn.vaohang.PresenceProbe
import vn.vaohang.VaoHangApp
import vn.vaohang.core.LightsOut
import java.time.Instant
import java.time.ZoneId

private val Rock = Color(0xFF4A4440)
private val Dark = Color(0xFF0F0E0D)
private val Gold = Color(0xFFF2C200)
private val Red = Color(0xFFC8102E)
private val Mute = Color(0xFFB9B2A9)

@Composable
fun VaoHangTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Red, secondary = Gold, background = Dark, surface = Rock),
        content = content,
    )
}

@Composable
fun App(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    Box(Modifier.fillMaxSize().background(Dark)) {
        when (state.screen) {
            Screen.Onboarding -> OnboardingScreen(state.busy, vm::register)
            Screen.Home -> HomeScreen(state.groups, vm)
            is Screen.Cave -> state.cave?.let { CaveScreen(it, vm) }
            Screen.Celebrate -> CelebrateScreen(vm::home)
        }
        state.message?.let { msg ->
            AlertDialog(
                onDismissRequest = vm::dismissMessage,
                confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("OK") } },
                text = { Text(msg) },
            )
        }
    }
}

@Composable
private fun Title(text: String, color: Color = Color.White) =
    Text(text, color = color, fontSize = 28.sp, fontWeight = FontWeight.Black)

@Composable
private fun Body(text: String, color: Color = Mute) = Text(text, color = color, fontSize = 15.sp)

@Composable
fun OnboardingScreen(busy: Boolean, onDone: (String, Boolean, Boolean) -> Unit) {
    var nickname by remember { mutableStateOf("") }
    var measure by remember { mutableStateOf(true) }
    var daytimeWork by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(24.dp))
        Title("VÀO HANG")
        Body("MU thua thì rủ nhau vào hang. MU thắng thì cùng ra khỏi hang.", Color.White)
        OutlinedTextField(
            value = nickname, onValueChange = { nickname = it.take(40) },
            label = { Text("Biệt danh (anh em thấy tên này)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Body(
            "Luật trong hang:\n• Chỉ anh em đã cùng bật, hai bên đồng ý.\n• Không có ô chat, không chụp màn hình.\n" +
                "• Câu vỗ vai chỉ tự trào về MU, không chê ai.\n• Không điểm danh ai vắng.\n" +
                "• Không thu thập cảm xúc để bán quảng cáo hay cá cược.",
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = measure, onCheckedChange = { measure = it })
            Body("Cho phép đo ẩn danh: bạn tắt máy sau còi bao lâu (chỉ trong 50 phút sau trận).")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = daytimeWork, onCheckedChange = { daytimeWork = it })
            Body("Tôi đi làm 8:00–18:00 (vd. anh em ở Nhật) – giờ đó đừng gọi, để tối báo.")
        }
        Button(
            enabled = nickname.isNotBlank() && !busy,
            onClick = { onDone(nickname, measure, daytimeWork) },
            colors = ButtonDefaults.buttonColors(containerColor = Red),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Bắt đầu") }
    }
}

@Composable
fun HomeScreen(groups: List<GroupDto>, vm: AppViewModel) {
    var creating by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Title("Hội anh em")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { creating = true }, colors = ButtonDefaults.buttonColors(containerColor = Red)) {
                    Text("Tạo hội")
                }
                OutlinedButton(onClick = { joining = true }) { Text("Nhập mã mời") }
                TextButton(onClick = vm::refreshGroups) { Text("Tải lại", color = Mute) }
            }
        }
        if (groups.isEmpty()) item { Body("Chưa có hội nào. Tạo hội rồi gửi mã mời cho anh em.") }
        items(groups, key = { it.id }) { GroupCard(it, vm) }
    }
    if (creating) TextDialog("Tên hội", "Tạo", onDismiss = { creating = false }) { vm.createGroup(it); creating = false }
    if (joining) TextDialog("Mã mời (6 ký tự)", "Xin vào", onDismiss = { joining = false }) {
        vm.joinGroup(it); joining = false
    }
}

@Composable
private fun GroupCard(group: GroupDto, vm: AppViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = Rock), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(group.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (group.status == "pending") {
                Body("Đang đợi chủ hội duyệt.")
                return@Column
            }
            Body(group.members.joinToString(" · ") { it.nickname })
            group.inviteCode?.let { Body("Mã mời: $it", Gold) }
            group.pending.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Body("${p.nickname} xin vào", Color.White)
                    TextButton(onClick = { vm.approve(group.id, p.id) }) { Text("Duyệt", color = Gold) }
                }
            }
            val cave = group.cave
            if (cave != null) {
                Spacer(Modifier.height(4.dp))
                Text("Hang đang mở", color = Gold, fontWeight = FontWeight.Bold)
                Button(onClick = { vm.openCave(cave.id) }, colors = ButtonDefaults.buttonColors(containerColor = Red)) {
                    Text("Xem hang")
                }
            } else {
                Body("Hang đang đóng. MU thua thì hang mở.")
            }
        }
    }
}

@Composable
private fun TextDialog(label: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Thôi") } },
    )
}

@Composable
fun CaveScreen(cave: CaveView, vm: AppViewModel) {
    val context = LocalContext.current
    // Không chụp màn hình, không chuyển tiếp ra ngoài.
    DisposableEffect(Unit) {
        val window = (context as Activity).window
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    BackHandler(onBack = vm::home)
    val lightsOff = cave.meInside && cave.status == "OPEN" && !cave.lights.on
    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(onClick = vm::home) { Text("‹ Hội anh em", color = Mute) }
            Title(if (cave.lights.on) "Trong hang" else "Hang đang mở")
            cave.score?.let { Body(it, Color.White) }
        }
        item { Avatars(cave) }
        if (!cave.meInside && cave.status == "OPEN") {
            item {
                Body(if (cave.inside.isEmpty()) "Chưa ai trong hang." else "Vào hay không là bạn chọn.")
                Button(
                    onClick = { vm.enterCave(cave.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Vào hang") }
            }
        }
        if (lightsOff) item { LightsOutPanel() }
        items(cave.pats) { pat ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1917))) {
                Column(Modifier.padding(12.dp)) {
                    Text("${pat.from} vỗ vai", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("“${pat.text}”", color = Color.White)
                }
            }
        }
        if (cave.meInside && cave.lights.on) {
            item { Body("Vỗ vai lại (chọn một câu):") }
            items(cave.choices, key = { it.id }) { choice ->
                OutlinedButton(onClick = { vm.pat(cave.id, choice.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text(choice.text, color = Gold)
                }
            }
            item {
                OutlinedButton(onClick = { vm.invite(cave.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Rủ anh em vào hang", color = Color.White)
                }
            }
        }
        if (cave.meInside) {
            item {
                TextButton(onClick = { vm.leaveCave(cave.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Ra khỏi hang", color = Mute)
                }
            }
        }
    }
}

@Composable
private fun Avatars(cave: CaveView) {
    // Chỉ hiện người đang trong hang.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cave.inside.forEach { m ->
            Box(Modifier.size(40.dp).background(Gold, CircleShape), contentAlignment = Alignment.Center) {
                Text(m.nickname.take(1).uppercase(), color = Dark, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun LightsOutPanel() {
    val context = LocalContext.current
    val app = context.applicationContext as VaoHangApp
    val text = remember { LightsOut.text(Instant.now(), PresenceProbe(context, app.prefs).nextAlarm(), ZoneId.systemDefault()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("☾", color = Gold, fontSize = 48.sp)
        Text("Hang tắt đèn", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        text?.let { Body(it) }
        Spacer(Modifier.height(8.dp))
        Body("Hang vẫn mở tới khi MU thắng. Trận sau anh em vỗ vai tiếp.")
    }
}

@Composable
fun CelebrateScreen(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Red).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Title("RA KHỎI HANG!", Color.White)
        Spacer(Modifier.height(8.dp))
        Text("MU thắng rồi. Cả hội ra hang cùng lúc.", color = Color.White, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = Gold)) {
            Text("Ra hang ăn mừng", color = Dark)
        }
        Spacer(Modifier.height(8.dp))
        Text("“Giờ mới dám mở Facebook.”", color = Color(0xFFFFE9A8), fontSize = 14.sp)
    }
}
