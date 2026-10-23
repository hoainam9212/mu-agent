package vn.vaohang.ui

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.delay

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

/**
 * Nút "đất nặn": có độ dày (lớp đáy sẫm), mặt trên bóng nhẹ; bấm vào thì lún xuống.
 */
@Composable
private fun ClayButton(
    text: String,
    onClick: () -> Unit,
    base: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pulse: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = 5.dp
    val sink by animateDpAsState(if (pressed && enabled) depth else 0.dp, tween(90), label = "sink")
    val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
        1f, 1.04f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s",
    )
    val face = if (enabled) base else RockLight
    Box(
        modifier
            .scale(if (pulse && enabled) breathe else 1f)
            .padding(bottom = depth)
            .drawBehind {
                val r = CornerRadius(size.height / 2)
                val d = depth.toPx()
                val s = sink.toPx()
                drawRoundRect(Color.Black.copy(alpha = 0.35f), topLeft = Offset(0f, d + 3f), size = size, cornerRadius = r)
                drawRoundRect(lerp(face, Color.Black, 0.4f), topLeft = Offset(0f, d), size = size, cornerRadius = r)
                drawRoundRect(
                    Brush.verticalGradient(listOf(lerp(face, Color.White, 0.22f), face, lerp(face, Color.Black, 0.12f)), startY = s, endY = s + size.height),
                    topLeft = Offset(0f, s), size = size, cornerRadius = r,
                )
                drawRoundRect(
                    Color.White.copy(alpha = 0.16f), topLeft = Offset(size.width * 0.1f, s + size.height * 0.1f),
                    size = Size(size.width * 0.8f, size.height * 0.26f), cornerRadius = CornerRadius(size.height * 0.13f),
                )
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (enabled) textColor else Mute, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp, maxLines = 1, modifier = Modifier.offset(y = sink),
        )
    }
}

/** Nút chính: đất nặn đỏ MU. */
@Composable
private fun FireButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, pulse: Boolean = false) =
    ClayButton(text, onClick, MuRed, Color.White, modifier, enabled, pulse)

/** Nút phụ: đất nặn nâu đá. */
@Composable
private fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Gold) =
    ClayButton(text, onClick, ClayStone, color, modifier)

private val ClayStone = Color(0xFF5B4535)

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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CaveBlack.copy(alpha = 0.6f)).clickable { onChange(!checked) }.padding(16.dp),
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

/** Luật trong hang dạng biểu tượng: một hình, hai ba chữ. */
@Composable
private fun RuleBadge(icon: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(CaveBlack.copy(alpha = 0.6f)).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, fontSize = 26.sp)
        Text(text, color = Mute, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 15.sp)
    }
}

@Composable
fun OnboardingScreen(busy: Boolean, onDone: (String, Boolean, Boolean) -> Unit) {
    var nickname by remember { mutableStateOf("") }
    var measure by remember { mutableStateOf(true) }
    var daytimeWork by remember { mutableStateOf(false) }
    // Cả màn chào nằm trong hang: cảnh lửa ở trên, phần nhập tên trên nền đất tối.
    CaveBackdrop(fireOn = true, fireY = 0.31f) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        Box(Modifier.fillMaxWidth().height(330.dp)) {
            CampScene(DemoCrew, meId = 0, burning = true, height = 290.dp, modifier = Modifier.align(Alignment.BottomCenter))
        }
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "VÀO HANG", color = Ink, fontSize = 52.sp, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = nickname, onValueChange = { nickname = it.take(40) },
                label = { Text("Biệt danh trong hang") }, placeholder = { Text("vd. Tuấn Tối Cổ", color = Mute) },
                singleLine = true, colors = caveFieldColors(), shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuleBadge("🤝", "Chỉ anh em", Modifier.weight(1f))
                RuleBadge("🤐", "Không chat", Modifier.weight(1f))
                RuleBadge("📵", "Không chụp", Modifier.weight(1f))
                RuleBadge("🙅", "Không chê", Modifier.weight(1f))
            }
            ConsentRow("🔥 Lửa tắt là tắt máy", "Hang đếm xem anh em có đi ngủ sớm không · không ghi tên", measure) { measure = it }
            ConsentRow("💼 Ban ngày đi làm", "8:00–18:00 hang không gọi, tối mới báo", daytimeWork) { daytimeWork = it }
            FireButton(
                "🔥  Vào hang", onClick = { onDone(nickname, measure, daytimeWork) },
                enabled = nickname.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }
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
        Text("⚽", fontSize = 96.sp, modifier = Modifier.align(Alignment.CenterEnd).offset(x = 36.dp).alpha(0.10f))
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
                color = Color.White, fontSize = 28.sp, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "${m.competition} · ${Weekdays[local.dayOfWeek.value - 1]} ${local.dayOfMonth}/${local.monthValue} · " +
                    "%02d:%02d".format(local.hour, local.minute) + if (local.hour < 6) "  🌙" else "",
                color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp,
            )
        }
    }
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
            CaveMouth(open, Modifier.size(84.dp, 70.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, color = Ink, fontSize = 22.sp, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold)
                if (group.status == "pending") Text("⏳ Chờ duyệt", color = Mute, fontSize = 14.sp)
                else Text("👥 ${group.members.size}", color = Mute, fontSize = 14.sp)
            }
        }
        if (group.status == "pending") return@Column
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            group.members.forEach { Caveman(it.nickname, it.id, width = 46.dp, me = it.id == meId, asleep = !open) }
        }
        if (open && cave != null) {
            FireButton("🔥  Vào hang", onClick = { actions.openCave(cave.id) }, pulse = true, modifier = Modifier.fillMaxWidth())
        }
        group.inviteCode?.let { code ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CaveBlack.copy(alpha = 0.5f)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Kicker("🔑 MÃ MỜI", Mute)
                    Text(code, color = Gold, fontSize = 28.sp, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                }
                GhostButton("📤 Gửi", onClick = {
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
                Text("${p.nickname} 🙋", color = Ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                FireButton("✓ Duyệt", onClick = { actions.approve(group.id, p.id) })
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
                        "Chào ${state.nickname ?: "anh em"} 👋", color = Ink, fontSize = 30.sp, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold,
                    )
                }
                TextButton(onClick = actions::refreshGroups) { Text("↻", color = Mute, fontSize = 24.sp) }
            }
        }
        state.nextMatch?.let { m -> item { NextMatchCard(m) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FireButton("＋  Tạo hội", onClick = { creating = true }, modifier = Modifier.weight(1f))
                GhostButton("🔑  Nhập mã", onClick = { joining = true }, modifier = Modifier.weight(1f))
            }
        }
        if (state.groups.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Campfire(burning = false, modifier = Modifier.size(120.dp))
                    Text("Chưa có hội nào", color = Mute, fontSize = 16.sp)
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
        score, color = Color.White, fontSize = 20.sp, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔥", fontSize = 20.sp, modifier = Modifier.weight(1f))
            Text("%d:%02d".format(left.toMinutes(), left.seconds % 60), color = Ember, fontSize = 22.sp, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold)
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
            Text(from, color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

/** Lửa tắt: chỉ hiện giờ báo thức nếu có (nhân vật đã ngủ 💤, không cần thêm chữ). */
@Composable
private fun FireOutPanel() {
    val context = LocalContext.current
    val alarm = remember {
        val app = context.applicationContext as? VaoHangApp
        app?.let { LightsOut.text(Instant.now(), PresenceProbe(context, it.prefs).nextAlarm(), ZoneId.systemDefault()) }
    }
    alarm ?: return
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Rock.copy(alpha = 0.85f)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⏰", fontSize = 30.sp)
        Spacer(Modifier.width(14.dp))
        Body(alarm, Gold)
    }
}

/** Độ cao (tỉ lệ màn hình) của đống lửa trên nền hang. */
private const val FIRE_Y = 0.6f
private val SceneHeight = 330.dp

/** Câu vỗ vai gần nhất của mỗi người (tối đa 3 người), để hiện thành bong bóng trên đầu họ. */
private fun recentSayings(cave: CaveView): Map<Long, String> {
    val idByName = cave.inside.associate { it.nickname to it.id }
    val latest = LinkedHashMap<Long, String>()
    cave.pats.asReversed().forEach { p ->
        val id = idByName[p.from] ?: return@forEach
        if (id !in latest && latest.size < 3) latest[id] = p.text
    }
    return latest
}

@Composable
private fun ChoiceSheet(cave: CaveView, onPick: (Long) -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onClose)) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Rock)
                // Chặn chạm xuyên xuống lớp nền (chạm nền là đóng).
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).clip(RoundedCornerShape(50)).background(RockEdge))
            cave.choices.forEach { c -> PatChoice(c.text) { onPick(c.id) } }
            if (cave.pats.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                cave.pats.asReversed().forEach { PatBubble(it.from, it.text) }
            }
        }
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
    var choosing by remember { mutableStateOf(false) }
    BackHandler { if (choosing) choosing = false else actions.home() }
    // Tải lại đều đặn để thấy anh em mới vào và câu vỗ vai mới.
    LaunchedEffect(cave.id) {
        while (true) {
            delay(15_000)
            actions.refreshCaveQuietly(cave.id)
        }
    }
    val open = cave.status == "OPEN"
    val burning = open && cave.lights.on
    CaveBackdrop(fireOn = burning, fireY = FIRE_Y) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Sân khấu: đống lửa đặt đúng vệt sáng trên nền hang, cả hội ngồi quanh.
            val fireBase = maxHeight * FIRE_Y
            CampScene(
                cave.inside, meId, burning, said = if (burning) recentSayings(cave) else emptyMap(),
                height = SceneHeight,
                modifier = Modifier.align(Alignment.TopCenter)
                    .offset(y = fireBase - SceneHeight / 2 - SceneHeight * 0.19f),
            )
            Column(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = actions::home) { Text("‹  ${groupName ?: "Hội anh em"}", color = Ink.copy(alpha = 0.8f), fontSize = 15.sp) }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    cave.score?.let { ScoreBoard(it) }
                }
                if (burning) FireTimer(cave.lights.until, onExpired = { actions.refreshCaveQuietly(cave.id) })
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    !open -> {}
                    !cave.meInside ->
                        FireButton("🔥  Vào hang", onClick = { actions.enterCave(cave.id) }, pulse = true, modifier = Modifier.fillMaxWidth())
                    burning -> {
                        FireButton("👋  Vỗ vai", onClick = { choosing = true }, pulse = cave.pats.isEmpty(), modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GhostButton("📣  Rủ anh em", onClick = { actions.invite(cave.id) }, modifier = Modifier.weight(1f))
                            GhostButton("🚪  Ra hang", onClick = { actions.leaveCave(cave.id) }, color = Mute, modifier = Modifier.weight(1f))
                        }
                    }
                    else -> {
                        FireOutPanel()
                        GhostButton("🚪  Ra hang", onClick = { actions.leaveCave(cave.id) }, color = Mute, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (choosing && burning) {
                ChoiceSheet(cave, onPick = { id -> actions.pat(cave.id, id); choosing = false }, onClose = { choosing = false })
            }
        }
    }
}

// ---- Ra khỏi hang --------------------------------------------------------------------------

@Composable
fun CelebrateScreen(onDone: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val pop by animateFloatAsState(
        if (shown) 1f else 0.6f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow), label = "pop",
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
                "RA KHỎI HANG!", color = Color.White, fontSize = 48.sp, fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center, modifier = Modifier.scale(pop),
            )
            Spacer(Modifier.height(28.dp))
            ClayButton("🎉  Ăn mừng", onDone, Color.White, MuRed)
        }
    }
}
