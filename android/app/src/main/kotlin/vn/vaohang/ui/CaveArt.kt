package vn.vaohang.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import vn.vaohang.Member
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---- Nền hang ------------------------------------------------------------------------------

private val RockDeep = Color(0xFF120E0B)
private val RockLit = Color(0xFF6A4A33)

// Độ dài nhũ đá trên trần hang (tỉ lệ theo bề ngang màn hình).
private val STALACTITES = floatArrayOf(0.07f, 0.16f, 0.05f, 0.11f, 0.20f, 0.06f, 0.13f, 0.04f, 0.18f, 0.08f, 0.14f, 0.05f)

// Độ lởm chởm của mép vách hang và hình dạng từng tảng đá (bảng số cố định để hình không nhảy).
private val JAG = floatArrayOf(0.9f, -0.4f, 0.3f, -1f, 0.6f, -0.2f, 1f, -0.7f, 0.1f, -0.5f, 0.8f, -0.9f, 0.4f, -0.1f)
private val ROCK = floatArrayOf(1f, 0.82f, 0.95f, 0.78f, 1.05f, 0.88f, 0.92f)

private fun rockPath(c: Offset, r: Float, seed: Int) = Path().apply {
    val n = ROCK.size
    for (i in 0 until n) {
        val t = 2 * PI * i / n + seed * 0.7
        val k = ROCK[(i + seed) % n] * r
        val p = Offset(c.x + cos(t).toFloat() * k, c.y + sin(t).toFloat() * k * 0.8f)
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private fun DrawScope.drawCave(lit: Float, flicker: Float) {
    val w = size.width
    val h = size.height
    val fire = Offset(w / 2, h * 0.47f)
    // Lòng hang: hình vòm lởm chởm, ngoài vòm là đá đặc.
    val cx = w / 2
    val cy = h * 0.56f
    val rx = w * 0.47f
    val ry = h * 0.62f
    val n = 56
    fun rim(i: Int, extra: Float = 0f): Offset {
        val t = 2 * PI * i / n
        val k = 1f + JAG[i % JAG.size] * 0.05f + extra
        return Offset(cx + cos(t).toFloat() * rx * k, cy + sin(t).toFloat() * ry * k)
    }
    val chamber = Path().apply {
        for (i in 0..n) rim(i).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) }
        close()
    }
    drawRect(RockDeep)
    drawPath(
        chamber,
        Brush.radialGradient(listOf(lerp(CaveDeep, Color(0xFF3A2418), lit), CaveDeep, CaveBlack), center = fire, radius = h * 0.6f),
    )
    // Ánh lửa hắt lên vách và trần.
    drawCircle(
        Brush.radialGradient(
            listOf(Ember.copy(alpha = 0.32f * lit * flicker), MuRed.copy(alpha = 0.08f * lit), Color.Transparent),
            center = fire, radius = h * 0.55f,
        ),
        radius = h * 0.55f, center = fire,
    )
    // Các tảng đá quanh mép vách: mặt quay về đống lửa được hắt sáng.
    for (i in 0 until n step 2) {
        val c = rim(i, 0.02f)
        val r = w * (0.07f + 0.03f * ((JAG[(i * 3) % JAG.size] + 1) / 2))
        val toFire = Offset(fire.x - c.x, fire.y - c.y)
        val d = toFire.getDistance().coerceAtLeast(1f)
        val near = (1f - d / (h * 0.75f)).coerceIn(0f, 1f)
        drawPath(rockPath(c, r, i), lerp(Rock, RockLight, 0.5f))
        val litFace = c + toFire * (r * 0.28f / d)
        drawPath(rockPath(litFace, r * 0.62f, i + 1), lerp(RockLight, RockLit, lit * near * flicker).copy(alpha = 0.9f))
    }
    // Nhũ đá trên trần, đầu nhũ ửng lửa.
    val step = w / STALACTITES.size
    STALACTITES.forEachIndexed { i, len ->
        val x = i * step
        val tip = Offset(x + step * 0.5f, len * w * 0.6f)
        val drip = Path().apply {
            moveTo(x + step * 0.15f, 0f)
            lineTo(tip.x, tip.y)
            lineTo(x + step * 0.85f, 0f)
            close()
        }
        drawPath(drip, Rock)
        drawCircle(RockLit.copy(alpha = 0.6f * lit), w * 0.008f, tip)
    }
    // Măng đá dưới nền hai bên.
    for ((fx, hgt) in listOf(0.08f to 0.10f, 0.16f to 0.06f, 0.86f to 0.09f, 0.93f to 0.12f)) {
        val base = h
        val spike = Path().apply {
            moveTo(w * (fx - 0.05f), base)
            lineTo(w * fx, base - h * hgt)
            lineTo(w * (fx + 0.05f), base)
            close()
        }
        drawPath(spike, Rock)
    }
}

/** Nền hang: vòm đá lởm chởm, nhũ đá trên trần, măng đá dưới nền; hắt ánh lửa khi lửa còn cháy. */
@Composable
fun CaveBackdrop(fireOn: Boolean, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val flicker by rememberInfiniteTransition(label = "torch").animateFloat(
        initialValue = 0.78f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "flicker",
    )
    val lit by animateFloatAsState(if (fireOn) 1f else 0f, tween(2000), label = "lit")
    Box(modifier.fillMaxSize().drawBehind { drawCave(lit, flicker) }, content = content)
}

// ---- Đống lửa ------------------------------------------------------------------------------

private val Stone = Color(0xFF5E5650)
private val LogBrown = Color(0xFF6B4226)
private val LogDark = Color(0xFF4A2D1A)
private val Smoke = Color(0xFF9A948C)

private fun DrawScope.flame(color: Color, cx: Float, base: Float, w: Float, h: Float, sway: Float) {
    val p = Path().apply {
        moveTo(cx - w / 2, base)
        cubicTo(cx - w / 2, base - h * 0.45f, cx - w * 0.15f + sway, base - h * 0.75f, cx + sway, base - h)
        cubicTo(cx + w * 0.15f + sway, base - h * 0.75f, cx + w / 2, base - h * 0.45f, cx + w / 2, base)
        close()
    }
    drawPath(p, color)
}

/** Lửa trại: cháy thì lửa nhảy, tàn lửa bay; tắt thì lụi dần còn than hồng và khói. */
@Composable
fun Campfire(burning: Boolean, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "fire")
    val a by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val b by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(780, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "t")
    val fire by animateFloatAsState(if (burning) 1f else 0f, tween(2500), label = "fire")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val base = h * 0.80f
        // ánh lửa hắt xuống nền đất
        drawOval(
            Brush.radialGradient(listOf(Ember.copy(alpha = 0.5f * fire), Color.Transparent), center = Offset(cx, base), radius = w * 0.5f),
            topLeft = Offset(cx - w * 0.5f, base - h * 0.12f), size = Size(w, h * 0.24f),
        )
        // vòng đá
        for (i in 0 until 8) {
            val ang = 2 * PI * i / 8
            val x = cx + cos(ang).toFloat() * w * 0.33f
            val y = base + sin(ang).toFloat() * h * 0.06f
            drawOval(Stone, topLeft = Offset(x - w * 0.06f, y - h * 0.03f), size = Size(w * 0.12f, h * 0.06f))
        }
        // củi bắt chéo
        drawLine(LogBrown, Offset(cx - w * 0.28f, base + h * 0.02f), Offset(cx + w * 0.22f, base - h * 0.08f), w * 0.08f, StrokeCap.Round)
        drawLine(LogDark, Offset(cx + w * 0.28f, base + h * 0.02f), Offset(cx - w * 0.22f, base - h * 0.08f), w * 0.08f, StrokeCap.Round)
        // than hồng: luôn âm ỉ, rõ hơn khi lửa đã tắt
        val glow = (0.45f + 0.55f * (1 - fire)) * (0.6f + 0.4f * a)
        drawCircle(MuRed.copy(alpha = glow), w * 0.035f, Offset(cx - w * 0.07f, base - h * 0.04f))
        drawCircle(Ember.copy(alpha = glow), w * 0.028f, Offset(cx + w * 0.05f, base - h * 0.05f))
        drawCircle(MuRed.copy(alpha = glow * 0.8f), w * 0.022f, Offset(cx + w * 0.01f, base - h * 0.01f))
        if (fire > 0.01f) {
            val fb = base - h * 0.04f
            flame(MuRed, cx, fb, w * 0.42f, h * 0.66f * fire * (0.9f + 0.1f * a), (a - 0.5f) * w * 0.05f)
            flame(Ember, cx, fb, w * 0.30f, h * 0.50f * fire * (0.86f + 0.14f * b), (b - 0.5f) * w * 0.07f)
            flame(Gold, cx, fb, w * 0.16f, h * 0.30f * fire * (0.82f + 0.18f * a), (0.5f - b) * w * 0.05f)
            for (i in 0 until 5) {
                val p = (t + i * 0.2f) % 1f
                val x = cx + sin((p * 3 + i) * 2 * PI).toFloat() * w * 0.12f
                val y = base - h * 0.35f - p * h * 0.55f
                drawCircle(Gold.copy(alpha = (1 - p) * fire), w * 0.012f * (1 + i % 2), Offset(x, y))
            }
        }
        val smoke = 1f - fire
        if (smoke > 0.01f) {
            for (i in 0 until 4) {
                val p = (t + i * 0.25f) % 1f
                val x = cx + sin((p * 2 + i) * PI).toFloat() * w * 0.08f
                drawCircle(Smoke.copy(alpha = 0.28f * (1 - p) * smoke), w * (0.05f + p * 0.12f), Offset(x, base - h * 0.15f - p * h * 0.7f))
            }
        }
    }
}

// ---- Người tối cổ mặc áo MU ------------------------------------------------------------------

private val Skins = listOf(Color(0xFFF1C27D), Color(0xFFE0AC69), Color(0xFFC68642), Color(0xFFFFDBAC), Color(0xFFD4A373))
private val Hairs = listOf(Color(0xFF2B1B10), Color(0xFF4A2C17), Color(0xFF1A1A1A), Color(0xFF6B3E1F), Color(0xFF3B2A20))
private val Pelt = Color(0xFFD9A066)
private val PeltSpot = Color(0xFF5A3A1E)
private val Bone = Color(0xFFF2EBDD)

private fun pick(seed: Long, salt: Int, n: Int) = Math.floorMod(seed * 31 + salt, n.toLong()).toInt()

private fun DrawScope.drawCaveman(seed: Long, me: Boolean, asleep: Boolean, cheer: Boolean) {
    val w = size.width
    val h = size.height
    val skin = Skins[pick(seed, 1, Skins.size)]
    val hair = Hairs[pick(seed, 2, Hairs.size)]
    val beard = pick(seed, 3, 3) != 0
    val prop = if (cheer) -1 else pick(seed, 4, 3) // 0: xương, 1: gậy, 2: hơ tay
    if (me) drawCircle(Gold.copy(alpha = 0.30f), w * 0.5f, Offset(w / 2, h * 0.55f))
    // chân xếp bằng + khố da báo
    drawOval(skin, topLeft = Offset(w * 0.08f, h * 0.82f), size = Size(w * 0.45f, h * 0.15f))
    drawOval(skin, topLeft = Offset(w * 0.47f, h * 0.82f), size = Size(w * 0.45f, h * 0.15f))
    drawOval(Pelt, topLeft = Offset(w * 0.16f, h * 0.72f), size = Size(w * 0.68f, h * 0.17f))
    drawCircle(PeltSpot, w * 0.03f, Offset(w * 0.32f, h * 0.80f))
    drawCircle(PeltSpot, w * 0.025f, Offset(w * 0.52f, h * 0.77f))
    drawCircle(PeltSpot, w * 0.03f, Offset(w * 0.68f, h * 0.82f))
    // áo đỏ MU, cổ trắng
    val shirt = Path().apply {
        moveTo(w * 0.27f, h * 0.46f)
        lineTo(w * 0.73f, h * 0.46f)
        lineTo(w * 0.84f, h * 0.78f)
        quadraticBezierTo(w * 0.5f, h * 0.84f, w * 0.16f, h * 0.78f)
        close()
    }
    drawPath(shirt, MuRed)
    drawCircle(MuRed, w * 0.11f, Offset(w * 0.25f, h * 0.52f))
    drawCircle(MuRed, w * 0.11f, Offset(w * 0.75f, h * 0.52f))
    val collar = Path().apply {
        moveTo(w * 0.39f, h * 0.46f)
        lineTo(w * 0.5f, h * 0.55f)
        lineTo(w * 0.61f, h * 0.46f)
    }
    drawPath(collar, Color.White, style = Stroke(width = w * 0.035f, cap = StrokeCap.Round))
    // tay
    val arm = w * 0.09f
    if (cheer) {
        drawLine(skin, Offset(w * 0.22f, h * 0.52f), Offset(w * 0.08f, h * 0.18f), arm, StrokeCap.Round)
        drawLine(skin, Offset(w * 0.78f, h * 0.52f), Offset(w * 0.92f, h * 0.18f), arm, StrokeCap.Round)
    } else {
        drawLine(skin, Offset(w * 0.22f, h * 0.56f), Offset(w * 0.20f, h * 0.78f), arm, StrokeCap.Round)
        drawLine(skin, Offset(w * 0.78f, h * 0.56f), Offset(w * 0.80f, h * 0.78f), arm, StrokeCap.Round)
    }
    when (prop) {
        0 -> {
            val from = Offset(w * 0.82f, h * 0.80f)
            val to = Offset(w * 0.95f, h * 0.56f)
            drawLine(Bone, from, to, w * 0.05f, StrokeCap.Round)
            for (end in listOf(from, to)) {
                drawCircle(Bone, w * 0.035f, end + Offset(-w * 0.025f, 0f))
                drawCircle(Bone, w * 0.035f, end + Offset(w * 0.025f, 0f))
            }
        }
        1 -> {
            drawLine(LogBrown, Offset(w * 0.84f, h * 0.82f), Offset(w * 0.95f, h * 0.52f), w * 0.06f, StrokeCap.Round)
            drawCircle(LogBrown, w * 0.075f, Offset(w * 0.95f, h * 0.49f))
        }
    }
    // đầu: tóc bù xù, lông mày rậm, râu
    val hc = Offset(w * 0.5f, h * 0.30f)
    val hr = w * 0.21f
    drawCircle(hair, hr * 1.12f, hc + Offset(0f, -hr * 0.12f))
    drawCircle(skin, hr, hc)
    val left = hc.x - hr * 1.05f
    val seg = hr * 2.1f / 6
    val spikes = Path().apply {
        moveTo(left, hc.y - hr * 0.2f)
        for (i in 0 until 6) {
            lineTo(left + (i + 0.5f) * seg, hc.y - hr * (1.25f + (i % 2) * 0.22f))
            lineTo(left + (i + 1) * seg, hc.y - hr * 0.55f)
        }
        lineTo(hc.x + hr * 1.05f, hc.y - hr * 0.2f)
        close()
    }
    drawPath(spikes, hair)
    drawLine(hair, Offset(hc.x - hr * 0.6f, hc.y - hr * 0.2f), Offset(hc.x + hr * 0.6f, hc.y - hr * 0.2f), hr * 0.22f, StrokeCap.Round)
    if (asleep) {
        drawLine(CaveBlack, Offset(hc.x - hr * 0.42f, hc.y + hr * 0.08f), Offset(hc.x - hr * 0.18f, hc.y + hr * 0.08f), hr * 0.08f, StrokeCap.Round)
        drawLine(CaveBlack, Offset(hc.x + hr * 0.18f, hc.y + hr * 0.08f), Offset(hc.x + hr * 0.42f, hc.y + hr * 0.08f), hr * 0.08f, StrokeCap.Round)
    } else {
        drawCircle(CaveBlack, hr * 0.11f, hc + Offset(-hr * 0.3f, hr * 0.06f))
        drawCircle(CaveBlack, hr * 0.11f, hc + Offset(hr * 0.3f, hr * 0.06f))
    }
    drawCircle(Color.Black.copy(alpha = 0.15f), hr * 0.17f, hc + Offset(0f, hr * 0.28f))
    if (beard) {
        drawArc(hair, 15f, 150f, true, topLeft = Offset(hc.x - hr, hc.y - hr * 0.55f), size = Size(hr * 2, hr * 1.9f))
    }
    val mouthY = hc.y + hr * 0.58f
    drawArc(
        if (beard) skin else CaveBlack, 20f, 140f, false,
        topLeft = Offset(hc.x - hr * 0.3f, mouthY - hr * 0.25f), size = Size(hr * 0.6f, hr * 0.3f),
        style = Stroke(width = hr * 0.09f, cap = StrokeCap.Round),
    )
}

/**
 * Một anh em trong hang: người tối cổ mặc áo đỏ MU, chữ cái đầu của tên in trên áo.
 * [asleep] khi lửa tắt (nhắm mắt, 💤); [cheer] giơ tay ăn mừng.
 */
@Composable
fun Caveman(
    name: String,
    seed: Long,
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
    me: Boolean = false,
    asleep: Boolean = false,
    cheer: Boolean = false,
    showName: Boolean = true,
) {
    val height = width * 1.2f
    Column(modifier.width(width + 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width, height)) {
            Canvas(Modifier.fillMaxSize().alpha(if (asleep) 0.7f else 1f)) { drawCaveman(seed, me, asleep, cheer) }
            Text(
                name.trim().take(1).uppercase(),
                color = Color.White, fontWeight = FontWeight.Black, fontSize = (width.value * 0.16f).sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = height * 0.56f),
            )
            if (asleep) Text("💤", fontSize = (width.value * 0.22f).sp, modifier = Modifier.align(Alignment.TopEnd))
        }
        if (showName) {
            Text(
                if (me) "Bạn" else name, color = if (me) Gold else Ink, fontSize = 12.sp,
                fontWeight = if (me) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val Dirt = Color(0xFF2A1C12)

/**
 * Cả hội ngồi quanh đống lửa trên nền đất hắt sáng; mỗi người đổ bóng dài ra xa lửa.
 * Người phía sau lửa vẽ trước, người phía trước vẽ đè lên.
 */
@Composable
fun CampScene(people: List<Member>, meId: Long, burning: Boolean, modifier: Modifier = Modifier, height: Dp = 330.dp) {
    val lit by animateFloatAsState(if (burning) 1f else 0f, tween(2500), label = "ground")
    val n = people.size
    // Số người chẵn thì xoay nửa bước, để không ai ngồi chắn ngay trước đống lửa.
    val start = -PI / 2 + if (n % 2 == 0 && n > 0) PI / n else 0.0
    // Đông người thì nhân vật nhỏ lại và vòng ngồi rộng ra (tối đa 10 người một hội).
    val avatar = when {
        n <= 5 -> 64.dp
        n <= 7 -> 54.dp
        else -> 46.dp
    }
    val rx = if (n <= 6) 118.0 else 132.0
    val ry = height.value * (if (n <= 6) 0.27 else 0.31)
    val seats = people.mapIndexed { i, m ->
        val angle = start + 2 * PI * i / n
        Triple(m, (cos(angle) * rx).toFloat(), (sin(angle) * ry + 6).toFloat())
    }
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().zIndex(-1000f)) {
            val fire = center + Offset(0f, height.toPx() * 0.19f)
            // Nền đất: vùng sáng cam quanh lửa (lửa tắt thì chỉ còn đất tối).
            val gx = (rx.toFloat() + 85f).dp.toPx()
            val gy = (ry.toFloat() + 45f).dp.toPx()
            scale(1f, gy / gx, pivot = fire) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(
                            lerp(Dirt, Gold, 0.55f * lit).copy(alpha = 0.9f),
                            lerp(Dirt, Ember, 0.6f * lit).copy(alpha = 0.6f),
                            Color.Transparent,
                        ),
                        center = fire, radius = gx,
                    ),
                    radius = gx, center = fire,
                )
            }
            // Bóng đổ dài ra xa đống lửa.
            if (lit > 0.01f) {
                seats.forEach { (_, x, y) ->
                    val feet = center + Offset(x.dp.toPx(), (y + avatar.value * 0.45f).dp.toPx())
                    val dir = feet - fire
                    val u = dir / dir.getDistance().coerceAtLeast(1f)
                    val perp = Offset(-u.y, u.x)
                    val half = avatar.toPx() * 0.28f
                    val end = feet + u * (avatar.toPx() * 1.5f)
                    val shadow = Path().apply {
                        (feet + perp * half).let { moveTo(it.x, it.y) }
                        (end + perp * (half * 0.5f)).let { lineTo(it.x, it.y) }
                        (end - perp * (half * 0.5f)).let { lineTo(it.x, it.y) }
                        (feet - perp * half).let { lineTo(it.x, it.y) }
                        close()
                    }
                    drawPath(shadow, Brush.linearGradient(listOf(Color.Black.copy(alpha = 0.55f * lit), Color.Transparent), start = feet, end = end))
                }
            }
        }
        seats.forEach { (m, x, y) ->
            // Không hiện tên quanh lửa: hang đông thì tên đè nhau. Bạn có vầng sáng vàng.
            Caveman(
                m.nickname, m.id, width = avatar, me = m.id == meId, asleep = !burning, showName = false,
                modifier = Modifier.offset(x.dp, y.dp).zIndex(y),
            )
        }
        Campfire(burning, Modifier.size(height * 0.4f).offset(y = height * 0.07f).zIndex(0f))
    }
}

// ---- Cửa hang (thẻ hội) ----------------------------------------------------------------------

/** Cửa hang nhỏ: mở thì lửa bập bùng bên trong, đóng thì tảng đá chắn cửa. */
@Composable
fun CaveMouth(open: Boolean, modifier: Modifier = Modifier) {
    val a by rememberInfiniteTransition(label = "mouth").animateFloat(
        0f, 1f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), label = "a",
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val hill = Path().apply {
            moveTo(0f, h)
            cubicTo(0f, h * 0.05f, w, h * 0.05f, w, h)
            close()
        }
        drawPath(hill, RockLight)
        drawCircle(RockEdge, w * 0.04f, Offset(w * 0.18f, h * 0.62f))
        drawCircle(RockEdge, w * 0.03f, Offset(w * 0.84f, h * 0.70f))
        drawCircle(RockEdge, w * 0.025f, Offset(w * 0.30f, h * 0.30f))
        val mouth = Path().apply {
            moveTo(w * 0.30f, h)
            lineTo(w * 0.30f, h * 0.62f)
            cubicTo(w * 0.30f, h * 0.28f, w * 0.70f, h * 0.28f, w * 0.70f, h * 0.62f)
            lineTo(w * 0.70f, h)
            close()
        }
        drawPath(mouth, CaveBlack)
        if (open) {
            drawCircle(
                Brush.radialGradient(listOf(Ember.copy(alpha = 0.55f + 0.2f * a), Color.Transparent), center = Offset(w / 2, h * 0.86f), radius = w * 0.24f),
                radius = w * 0.24f, center = Offset(w / 2, h * 0.86f),
            )
            flame(MuRed, w / 2, h * 0.97f, w * 0.16f, h * 0.36f * (0.85f + 0.15f * a), (a - 0.5f) * w * 0.02f)
            flame(Gold, w / 2, h * 0.97f, w * 0.08f, h * 0.18f * (0.8f + 0.2f * a), (0.5f - a) * w * 0.02f)
        } else {
            drawCircle(Stone, w * 0.19f, Offset(w * 0.5f, h * 0.80f))
            drawCircle(Color.White.copy(alpha = 0.10f), w * 0.07f, Offset(w * 0.44f, h * 0.72f))
        }
    }
}

// ---- Pháo giấy ------------------------------------------------------------------------------

private data class Flake(val x: Float, val speed: Float, val w: Float, val color: Color, val phase: Float, val spin: Float)

/** Pháo giấy đỏ – vàng – trắng cho màn ra khỏi hang. */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val flakes = remember {
        val palette = listOf(MuRed, Gold, Color.White, RedDeep)
        Random(42).let { rnd ->
            List(90) {
                Flake(
                    rnd.nextFloat(), 0.6f + rnd.nextFloat(), 6f + rnd.nextFloat() * 10f, palette[rnd.nextInt(palette.size)],
                    rnd.nextFloat(), 180f + rnd.nextFloat() * 360f,
                )
            }
        }
    }
    val t by rememberInfiniteTransition(label = "confetti").animateFloat(
        0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "t",
    )
    Canvas(modifier) {
        flakes.forEach { f ->
            val progress = (t * f.speed + f.phase) % 1f
            val y = progress * (size.height + 60f) - 30f
            val x = f.x * size.width + sin((progress * 4 + f.phase) * 2 * PI).toFloat() * 24f
            rotate(progress * f.spin, pivot = Offset(x, y)) {
                drawRect(f.color, topLeft = Offset(x - f.w / 2, y - f.w / 4), size = Size(f.w, f.w / 2))
            }
        }
    }
}
