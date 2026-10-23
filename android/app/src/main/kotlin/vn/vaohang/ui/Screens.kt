package vn.vaohang.ui

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import vn.vaohang.CaveView
import vn.vaohang.GroupDto
import vn.vaohang.MatchDto
import vn.vaohang.Member
import vn.vaohang.PresenceProbe
import vn.vaohang.VaoHangApp
import vn.vaohang.core.LightsOut
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Thời gian lửa cháy sau mỗi trận không thắng (LIGHT_MINUTES trên máy chủ). */
private const val FIRE_MINUTES = 30

@Composable
fun App(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    Box(Modifier.fillMaxSize().background(CaveBlack)) {
        when (state.screen) {
            Screen.Onboarding -> OnboardingScreen(state.busy, vm::register)
            Screen.Home -> HomeScreen(state, vm)
            is Screen.Cave -> {
                val cave = state.cave
                if (cave != null) {
                    CaveScreen(cave, state.groups.firstOrNull { it.id == cave.groupId }?.name, state.meId, vm)
                } else {
                    CaveBackdrop(fireOn = false) {}
                }
            }
            Screen.Celebrate -> CelebrateScreen(vm::home)
        }
        if (state.busy) {
            LinearProgressIndicator(
                color = Ember, trackColor = Color.Transparent,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(2.dp),
            )
        }
        state.message?.let { msg ->
            AlertDialog(
                onDismissRequest = vm::dismissMessage,
                confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("OK", color = Gold) } },
                text = { Text(msg, color = Ink, fontSize = 16.sp) },
                containerColor = RockLight,
            )
        }
    }
}

// ---- Khối dùng chung ----------------------------------------------------------------------

@Composable
private fun Kicker(text: String, color: Color = Gold) =
    Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)

@Composable
private fun Body(text: String, color: Color = Mute, modifier: Modifier = Modifier, align: TextAlign? = null) =
    Text(text, color = color, fontSize = 15.sp, lineHeight = 21.sp, modifier = modifier, textAlign = align)

@Composable
private fun SectionTitle(text: String) =
    Text(text, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

/** Nút chính màu đỏ MU, có thể "thở" (phóng to nhẹ) để gọi bấm. */
@Composable
private fun FireButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, pulse: Boolean = false) {
    val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
        1f, 1.04f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s",
    )
    Button(
        onClick = onClick, enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MuRed, contentColor = Color.White,
            disabledContainerColor = RockLight, disabledContentColor = Mute,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        modifier = modifier
            .scale(if (pulse && enabled) breathe else 1f)
            .shadow(if (enabled) 14.dp else 0.dp, RoundedCornerShape(50), spotColor = MuRed, ambientColor = MuRed),
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Gold) {
    OutlinedButton(
        onClick = onClick,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, color.copy(alpha = 0.7f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        modifier = modifier,
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun caveFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Gold, unfocusedBorderColor = RockEdge,
    focusedLabelColor = Gold, unfocusedLabelColor = Mute,
    focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Gold,
)

@Composable
private fun TextDialog(label: String, confirm: String, code: Boolean = false, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RockLight,
        title = { Text(label, color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = if (code) it.uppercase().filter(Char::isLetterOrDigit).take(6) else it.take(40) },
                singleLine = true, colors = caveFieldColors(),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = if (code) 24.sp else 18.sp, letterSpacing = if (code) 6.sp else 0.sp,
                    fontWeight = if (code) FontWeight.Bold else FontWeight.Normal,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = if (code) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences,
                ),
            )
        },
        confirmButton = {
            TextButton(enabled = if (code) text.length == 6 else text.isNotBlank(), onClick = { onConfirm(text) }) {
                Text(confirm, color = Gold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Thôi", color = Mute) } },
    )
}

// ---- Chào mừng -----------------------------------------------------------------------------

private val DemoCrew = listOf(Member(-11, "Tuấn"), Member(-12, "Hùng"), Member(-13, "Long"), Member(-14, "Đức"))

@Composable
private fun ConsentRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Rock).clickable { onChange(!checked) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(detail, color = Mute, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MuRed, checkedThumbColor = Color.White, uncheckedTrackColor = RockLight),
        )
    }
}

@Composable
private fun StepRow(icon: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(RockLight), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 22.sp)
        }
        Spacer(Modifier.width(14.dp))
        Body(text, Ink)
    }
}

@Composable
fun OnboardingScreen(busy: Boolean, onDone: (String, Boolean, Boolean) -> Unit) {
    var nickname by remember { mutableStateOf("") }
    var measure by remember { mutableStateOf(true) }
    var daytimeWork by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        CaveBackdrop(fireOn = true, modifier = Modifier.fillMaxWidth().height(330.dp)) {
            CampScene(DemoCrew, meId = 0, burning = true, height = 290.dp, modifier = Modifier.align(Alignment.BottomCenter))
        }
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("VÀO HANG", color = Ink, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text(
                "MU thua thì cả hội rủ nhau vào hang, ngồi quanh đống lửa. MU thắng thì cùng ra khỏi hang.",
                color = Ink, fontSize = 17.sp, lineHeight = 24.sp,
            )
            StepRow("💔", "MU thua → hang mở cho hội anh em của bạn.")
            StepRow("🤝", "Ngồi quanh lửa vỗ vai nhau bằng câu soạn sẵn. Không có ô chat.")
            StepRow("🔥", "30 phút sau lửa tắt, cả hội đi ngủ. Hang chỉ đóng khi MU thắng.")
            OutlinedTextField(
                value = nickname, onValueChange = { nickname = it.take(40) },
                label = { Text("Biệt danh trong hang") }, placeholder = { Text("vd. Tuấn Tối Cổ", color = Mute) },
                singleLine = true, colors = caveFieldColors(), shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Rock).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Kicker("LUẬT TRONG HANG")
                listOf(
                    "Chỉ anh em đã đồng ý cùng hội mới thấy nhau.",
                    "Không chat, không chụp màn hình trong hang.",
                    "Câu vỗ vai chỉ tự trào về MU, không chê ai.",
                    "Không điểm danh ai vắng mặt.",
                    "Không bán cảm xúc của bạn cho quảng cáo hay cá cược.",
                ).forEach { Body("✓  $it", Ink) }
            }
            ConsentRow("Đo giấc ngủ ẩn danh", "Bạn tắt máy bao lâu sau còi (chỉ trong 50 phút sau trận).", measure) { measure = it }
            ConsentRow("Tôi đi làm 8:00–18:00", "Giờ đó đừng gọi, để tối báo (vd. anh em ở Nhật).", daytimeWork) { daytimeWork = it }
            FireButton(
                "🔥  Nhóm lửa, vào thôi", onClick = { onDone(nickname, measure, daytimeWork) },
                enabled = nickname.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---- Hội anh em ----------------------------------------------------------------------------

private val Weekdays = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

@Composable
private fun NextMatchCard(m: MatchDto) {
    val kickoff = remember(m.kickoff) { runCatching { Instant.parse(m.kickoff) }.getOrNull() } ?: return
    val local = kickoff.atZone(ZoneId.systemDefault())
    val left = Duration.between(Instant.now(), kickoff)
    val countdown = when {
        m.status == "LIVE" || left.isNegative -> "Đang đá"
        left.toHours() < 1 -> "còn ${left.toMinutes()} phút"
        left.toHours() < 24 -> "còn ${left.toHours()} giờ"
        else -> "còn ${left.toDays()} ngày"
    }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(RedDeep, MuRed)))
            .padding(20.dp),
    ) {
        Text("⚽", fontSize = 72.sp, modifier = Modifier.align(Alignment.TopEnd).alpha(0.18f))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Kicker("TRẬN TỚI", Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.width(10.dp))
                Text(
                    countdown, color = CaveBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Gold).padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
            Text(
                if (m.home) "MU  –  ${m.opponent}" else "${m.opponent}  –  MU",
                color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black,
            )
            Text(
                "${m.competition} · ${Weekdays[local.dayOfWeek.value - 1]} ${local.dayOfMonth}/${local.monthValue} · " +
                    "%02d:%02d".format(local.hour, local.minute),
                color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp,
            )
            if (local.hour < 6) Text("🌙 Trận đêm – app sẽ nhắc trước giờ bóng lăn", color = Gold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StatusChip(open: Boolean) {
    val (text, bg, fg) = if (open) Triple("🔥 HANG ĐANG MỞ", MuRed, Color.White) else Triple("Hang đóng", RockLight, Mute)
    Text(
        text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun GroupCard(group: GroupDto, meId: Long, actions: UiActions) {
    val context = LocalContext.current
    val cave = group.cave
    val open = cave != null && cave.status == "OPEN"
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(if (open) listOf(Color(0xFF3A1411), Rock) else listOf(RockLight, Rock)))
            .border(1.5.dp, if (open) Brush.linearGradient(listOf(MuRed, Ember, Gold)) else SolidColor(RockEdge), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(group.name, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (group.status != "pending") StatusChip(open)
        }
        if (group.status == "pending") {
            Body("⏳ Đang đợi chủ hội duyệt. Duyệt xong là bạn thấy hang của hội.")
            return@Column
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            group.members.forEach { Caveman(it.nickname, it.id, width = 46.dp, me = it.id == meId) }
        }
        if (open && cave != null) {
            Body("MU vừa thua. Hang mở rồi, anh em đang quây quanh lửa.", Ink)
            FireButton("🔥  Vào xem hang", onClick = { actions.openCave(cave.id) }, pulse = true, modifier = Modifier.fillMaxWidth())
        } else {
            Body("MU thua thì hang mở, cả hội vào ngồi quanh lửa.")
        }
        group.inviteCode?.let { code ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CaveBlack.copy(alpha = 0.5f)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Kicker("MÃ MỜI", Mute)
                    Text(code, color = Gold, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                }
                GhostButton("Gửi mã", onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(
                        Intent.EXTRA_TEXT,
                        "Vào hội \"${group.name}\" trên app Vào Hang nhé – MU thua thì cả hội vào hang 🔥\nMã mời: $code",
                    )
                    context.startActivity(Intent.createChooser(send, "Gửi mã mời"))
                })
            }
        }
        group.pending.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Caveman(p.nickname, p.id, width = 36.dp, showName = false)
                Text("${p.nickname} xin vào hội", color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                FireButton("Duyệt", onClick = { actions.approve(group.id, p.id) })
            }
        }
    }
}

@Composable
fun HomeScreen(state: UiState, actions: UiActions) {
    var creating by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(CaveDeep, CaveBlack))).systemBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Kicker("VÀO HANG")
                    Text(
                        "Chào ${state.nickname ?: "anh em"} 👋", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black,
                    )
                }
                TextButton(onClick = actions::refreshGroups) { Text("↻", color = Mute, fontSize = 24.sp) }
            }
        }
        state.nextMatch?.let { m -> item { NextMatchCard(m) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FireButton("＋  Tạo hội", onClick = { creating = true }, modifier = Modifier.weight(1f))
                GhostButton("Nhập mã mời", onClick = { joining = true }, modifier = Modifier.weight(1f))
            }
        }
        if (state.groups.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Campfire(burning = false, modifier = Modifier.size(120.dp))
                    Text("Chưa có hội nào", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Body("Tạo hội rồi gửi mã mời cho anh em, hoặc nhập mã anh em gửi.", align = TextAlign.Center)
                }
            }
        } else {
            item { SectionTitle("Hội anh em") }
            items(state.groups, key = { it.id }) { GroupCard(it, state.meId, actions) }
        }
    }
    if (creating) TextDialog("Đặt tên hội", "Tạo hội", onDismiss = { creating = false }) { actions.createGroup(it); creating = false }
    if (joining) TextDialog("Mã mời (6 ký tự)", "Xin vào", code = true, onDismiss = { joining = false }) {
        actions.joinGroup(it); joining = false
    }
}

// ---- Trong hang ----------------------------------------------------------------------------

@Composable
private fun ScoreBoard(score: String) {
    Text(
        score, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(RedDeep, MuRed)))
            .padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

/** "Lửa còn cháy 23:14" + thanh lửa cạn dần. Hết giờ thì báo để tải lại màn hang. */
@Composable
private fun FireTimer(until: String?, onExpired: () -> Unit) {
    val end = remember(until) { until?.let { runCatching { Instant.parse(it) }.getOrNull() } } ?: return
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(end) {
        while (now.isBefore(end)) {
            delay(1000)
            now = Instant.now()
        }
        onExpired()
    }
    val left = Duration.between(now, end).let { if (it.isNegative) Duration.ZERO else it }
    val fraction = (left.seconds / (FIRE_MINUTES * 60f)).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text("🔥 Lửa còn cháy", color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("%d:%02d".format(left.toMinutes(), left.seconds % 60), color = Ember, fontWeight = FontWeight.Black)
        }
        LinearProgressIndicator(
            progress = { fraction }, color = Ember, trackColor = RockLight,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
        )
    }
}

@Composable
private fun PatBubble(from: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Caveman(from, from.hashCode().toLong(), width = 34.dp, showName = false)
        Column(
            Modifier.clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp))
                .background(RockLight).padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text("$from vỗ vai", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("“$text”", color = Ink, fontSize = 15.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun PatChoice(text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Rock.copy(alpha = 0.9f))
            .border(1.dp, Gold.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("👋", fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Text(text, color = Ink, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun FireOutPanel() {
    val context = LocalContext.current
    val alarm = remember {
        val app = context.applicationContext as? VaoHangApp
        app?.let { LightsOut.text(Instant.now(), PresenceProbe(context, it.prefs).nextAlarm(), ZoneId.systemDefault()) }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Rock.copy(alpha = 0.85f)).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Lửa tắt rồi", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Body("Cả hội đi ngủ thôi.", Ink)
        alarm?.let { Body(it, Gold, align = TextAlign.Center) }
        Body("Hang vẫn mở tới khi MU thắng. Trận sau anh em nhóm lửa tiếp.", align = TextAlign.Center)
    }
}

private fun sceneCaption(cave: CaveView, burning: Boolean): String {
    val n = cave.inside.size
    return when {
        n == 0 -> "Chưa ai ngồi bên lửa."
        !burning -> "Lửa tắt rồi, anh em ngủ cả."
        cave.meInside && n == 1 -> "Mình bạn bên đống lửa. Rủ anh em vào đi!"
        else -> "$n anh em đang ngồi quanh lửa"
    }
}

@Composable
fun CaveScreen(cave: CaveView, groupName: String?, meId: Long, actions: UiActions) {
    val context = LocalContext.current
    // Không chụp màn hình, không chuyển tiếp ra ngoài.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    BackHandler(onBack = actions::home)
    // Tải lại đều đặn để thấy anh em mới vào và câu vỗ vai mới.
    LaunchedEffect(cave.id) {
        while (true) {
            delay(15_000)
            actions.refreshCaveQuietly(cave.id)
        }
    }
    val open = cave.status == "OPEN"
    val burning = open && cave.lights.on
    CaveBackdrop(fireOn = burning) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextButton(onClick = actions::home) { Text("‹  ${groupName ?: "Hội anh em"}", color = Mute, fontSize = 15.sp) }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                cave.score?.let { ScoreBoard(it) }
            }
            if (burning) {
                FireTimer(cave.lights.until, onExpired = { actions.refreshCaveQuietly(cave.id) })
            } else if (open) {
                Body("Lửa đã tắt · hang vẫn mở tới khi MU thắng", align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            CampScene(cave.inside, meId, burning)
            Body(sceneCaption(cave, burning), Ink, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            when {
                !open -> Body("Hang đã đóng – MU thắng rồi!", Gold, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                !cave.meInside -> {
                    Body("Vào hay không là bạn chọn.", align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    FireButton("🔥  VÀO HANG NGỒI CÙNG", onClick = { actions.enterCave(cave.id) }, pulse = true, modifier = Modifier.fillMaxWidth())
                }
                else -> {
                    if (!burning) FireOutPanel()
                    if (cave.pats.isNotEmpty()) {
                        SectionTitle("Anh em vỗ vai")
                        cave.pats.forEach { PatBubble(it.from, it.text) }
                    }
                    if (burning && cave.choices.isNotEmpty()) {
                        SectionTitle("Vỗ vai anh em – chọn một câu")
                        cave.choices.forEach { c -> PatChoice(c.text) { actions.pat(cave.id, c.id) } }
                    }
                    if (burning) {
                        GhostButton("📣  Rủ anh em đang thức", onClick = { actions.invite(cave.id) }, modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = { actions.leaveCave(cave.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ra khỏi hang", color = Mute)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ---- Ra khỏi hang --------------------------------------------------------------------------

@Composable
fun CelebrateScreen(onDone: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(
        if (shown) 1f else 0.3f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow), label = "pop",
    )
    val t by rememberInfiniteTransition(label = "jump").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "t",
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MuRed, Ember, Gold)))) {
        Confetti(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(-21L, -22L, -23L).forEachIndexed { i, seed ->
                    val jump = abs(sin((t + i * 0.3f) * PI)).toFloat() * 28f
                    Caveman("MU", seed, width = 76.dp, cheer = true, showName = false, modifier = Modifier.offset(y = (-jump).dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "RA KHỎI HANG!", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center, modifier = Modifier.scale(pop),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "MU thắng rồi! Cả hội rời hang, ra ánh sáng.", color = Color.White, fontSize = 17.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = MuRed),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
            ) { Text("🎉  Ăn mừng thôi", fontSize = 17.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.height(16.dp))
            Text("“Giờ mới dám mở Facebook.”", color = Color.White.copy(alpha = 0.9f), fontStyle = FontStyle.Italic)
        }
    }
}
