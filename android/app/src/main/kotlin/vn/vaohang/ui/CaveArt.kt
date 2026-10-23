package vn.vaohang.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import vn.vaohang.Member
import vn.vaohang.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// ---- Nền hang ------------------------------------------------------------------------------

// Vị trí vệt sáng trên ảnh nền (đo từ ảnh): đống lửa đặt đúng chỗ này.
private const val GLOW_X = 0.524f
private const val GLOW_Y = 0.755f
private val FloorLit = Color(0xFF34261C)
private val FloorDark = Color(0xFF181D22)

/**
 * Nền hang đất nặn: ảnh hang sáng lửa và ảnh hang tối, chuyển dần theo lửa.
 * Ảnh được kéo sao cho vệt sáng trên nền đất nằm ở [fireY] (tỉ lệ chiều cao màn hình);
 * phần dưới còn trống tô màu nền đất cho liền.
 */
@Composable
fun CaveBackdrop(fireOn: Boolean, modifier: Modifier = Modifier, fireY: Float = 0.62f, content: @Composable BoxScope.() -> Unit) {
    val litImage = ImageBitmap.imageResource(R.drawable.cave_lit)
    val darkImage = ImageBitmap.imageResource(R.drawable.cave_dark)
    val flicker by rememberInfiniteTransition(label = "torch").animateFloat(
        initialValue = 0.78f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "flicker",
    )
    val lit by animateFloatAsState(if (fireOn) 1f else 0f, tween(2000), label = "lit")
    Box(
        modifier.fillMaxSize().drawBehind {
            val iw = litImage.width.toFloat()
            val ih = litImage.height.toFloat()
            val s = maxOf(size.width / iw, size.height / ih)
            val dw = iw * s
            val dh = ih * s
            val dx = (size.width / 2 - GLOW_X * dw).coerceIn(size.width - dw, 0f)
            val dy = minOf(fireY * size.height - GLOW_Y * dh, 0f)
            val floor = lerp(FloorDark, FloorLit, lit)
            drawRect(floor)
            val at = IntOffset(dx.roundToInt(), dy.roundToInt())
            val sz = IntSize(dw.roundToInt(), dh.roundToInt())
            drawImage(darkImage, dstOffset = at, dstSize = sz, filterQuality = FilterQuality.Medium)
            if (lit > 0.01f) drawImage(litImage, dstOffset = at, dstSize = sz, alpha = lit, filterQuality = FilterQuality.Medium)
            val bottom = dy + dh
            if (bottom < size.height) {
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, floor), startY = bottom - 160f, endY = bottom),
                    topLeft = Offset(0f, bottom - 160f), size = Size(size.width, 161f),
                )
            }
            // Ánh lửa bập bùng hắt lên vách.
            val fire = Offset(size.width / 2, fireY * size.height)
            drawCircle(
                Brush.radialGradient(listOf(Ember.copy(alpha = 0.22f * lit * flicker), Color.Transparent), center = fire, radius = size.width * 0.9f),
                radius = size.width * 0.9f, center = fire,
            )
            // Tối hai mép trên/dưới cho chữ và nút dễ đọc.
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), endY = size.height * 0.2f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)), startY = size.height * 0.78f))
        },
        content = content,
    )
}

// ---- Đống lửa ------------------------------------------------------------------------------

private val Stone = Color(0xFF5E5650)
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

/** Lửa trại đất nặn: lửa nhún nhảy, hắt sáng, tàn lửa bay; tắt thì còn than và khói. */
@Composable
fun Campfire(burning: Boolean, modifier: Modifier = Modifier) {
    val tr = rememberInfiniteTransition(label = "fire")
    val a by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "t")
    val fire by animateFloatAsState(if (burning) 1f else 0f, tween(2500), label = "fire")
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width / 2, size.height * 0.55f)
            val r = size.width * 0.8f
            drawCircle(
                Brush.radialGradient(
                    listOf(Gold.copy(alpha = 0.45f * fire * (0.8f + 0.2f * a)), Ember.copy(alpha = 0.22f * fire), Color.Transparent),
                    center = c, radius = r,
                ),
                radius = r, center = c,
            )
        }
        if (fire < 0.99f) {
            Image(painterResource(R.drawable.fire_out), null, Modifier.matchParentSize(), contentScale = ContentScale.Fit, alpha = 1f - fire)
        }
        if (fire > 0.01f) {
            Image(
                painterResource(R.drawable.fire_burning), null, contentScale = ContentScale.Fit, alpha = fire,
                modifier = Modifier.matchParentSize().graphicsLayer {
                    // lửa nhún lên xuống quanh gốc củi
                    scaleY = 1f + 0.04f * (a - 0.5f)
                    scaleX = 1f - 0.02f * (a - 0.5f)
                    transformOrigin = TransformOrigin(0.5f, 0.8f)
                },
            )
        }
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2
            val base = h * 0.62f
            if (fire > 0.01f) {
                for (i in 0 until 6) {
                    val p = (t + i / 6f) % 1f
                    val x = cx + sin((p * 3 + i) * 2 * PI).toFloat() * w * 0.14f
                    val y = base - h * 0.25f - p * h * 0.55f
                    drawCircle(Gold.copy(alpha = (1 - p) * fire), w * 0.012f * (1 + i % 2), Offset(x, y))
                }
            }
            val smoke = 1f - fire
            if (smoke > 0.01f) {
                for (i in 0 until 4) {
                    val p = (t + i * 0.25f) % 1f
                    val x = cx + sin((p * 2 + i) * PI).toFloat() * w * 0.08f
                    drawCircle(Smoke.copy(alpha = 0.25f * (1 - p) * smoke), w * (0.05f + p * 0.12f), Offset(x, base - h * 0.1f - p * h * 0.6f))
                }
            }
        }
    }
}

// ---- Người tối cổ mặc áo MU (đất nặn) ----------------------------------------------------------

// 6 kiểu nhân vật, mỗi kiểu 3 tư thế. Mỗi người luôn giữ một kiểu (theo id).
private val SitSprites = intArrayOf(
    R.drawable.caveman_0_sit, R.drawable.caveman_1_sit, R.drawable.caveman_2_sit,
    R.drawable.caveman_3_sit, R.drawable.caveman_4_sit, R.drawable.caveman_5_sit,
)
private val SleepSprites = intArrayOf(
    R.drawable.caveman_0_sleep, R.drawable.caveman_1_sleep, R.drawable.caveman_2_sleep,
    R.drawable.caveman_3_sleep, R.drawable.caveman_4_sleep, R.drawable.caveman_5_sleep,
)
private val CheerSprites = intArrayOf(
    R.drawable.caveman_0_cheer, R.drawable.caveman_1_cheer, R.drawable.caveman_2_cheer,
    R.drawable.caveman_3_cheer, R.drawable.caveman_4_cheer, R.drawable.caveman_5_cheer,
)

fun cavemanVariant(seed: Long): Int = Math.floorMod(seed, SitSprites.size.toLong()).toInt()

/**
 * Một anh em trong hang: người tối cổ đất nặn mặc áo đỏ MU, thở nhẹ.
 * [asleep] khi lửa tắt (nằm ngủ); [cheer] nhảy mừng; [me] có vầng sáng vàng dưới chân.
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
    val v = cavemanVariant(seed)
    val sprite = when {
        cheer -> CheerSprites[v]
        asleep -> SleepSprites[v]
        else -> SitSprites[v]
    }
    val height = width * 1.3f
    val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
        0f, 1f,
        infiniteRepeatable(
            tween(if (asleep) 2600 else 1700, easing = FastOutSlowInEasing), RepeatMode.Reverse,
            initialStartOffset = StartOffset(v * 290),
        ),
        label = "b",
    )
    Column(modifier.width(width + 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width + 16.dp, height), contentAlignment = Alignment.BottomCenter) {
            if (me) {
                Canvas(Modifier.matchParentSize()) {
                    val c = Offset(size.width / 2, size.height * 0.94f)
                    scale(1f, 0.32f, pivot = c) {
                        drawCircle(
                            Brush.radialGradient(listOf(Gold.copy(alpha = 0.9f), Gold.copy(alpha = 0.35f), Color.Transparent), center = c, radius = size.width * 0.62f),
                            radius = size.width * 0.62f, center = c,
                        )
                    }
                }
            }
            Image(
                painterResource(sprite), contentDescription = name, contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleY = 1f + 0.025f * breathe
                    scaleX = 1f - 0.01f * breathe
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
            )
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
fun CampScene(
    people: List<Member>,
    meId: Long,
    burning: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 330.dp,
    said: Map<Long, String> = emptyMap(),
) {
    val lit by animateFloatAsState(if (burning) 1f else 0f, tween(2500), label = "ground")
    val n = people.size
    // Số người chẵn thì xoay nửa bước, để không ai ngồi chắn ngay trước đống lửa.
    val start = -PI / 2 + if (n % 2 == 0 && n > 0) PI / n else 0.0
    // Đông người thì nhân vật nhỏ lại và vòng ngồi rộng ra (tối đa 10 người một hội).
    val avatar = when {
        n <= 5 -> 84.dp
        n <= 7 -> 70.dp
        else -> 58.dp
    }
    val rx = if (n <= 6) 124.0 else 134.0
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
            // Bóng tiếp đất ngay dưới mỗi người.
            seats.forEach { (_, x, y) ->
                val feet = center + Offset(x.dp.toPx(), (y + avatar.value * 0.6f).dp.toPx())
                scale(1f, 0.3f, pivot = feet) { drawCircle(Color.Black.copy(alpha = 0.4f), avatar.toPx() * 0.5f, feet) }
            }
            // Bóng đổ dài ra xa đống lửa.
            if (lit > 0.01f) {
                seats.forEach { (_, x, y) ->
                    val feet = center + Offset(x.dp.toPx(), (y + avatar.value * 0.6f).dp.toPx())
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
        // Câu vỗ vai hiện thành bong bóng ngay trên đầu người nói.
        seats.forEach { (m, x, y) ->
            val text = said[m.id] ?: return@forEach
            val bx = x.coerceIn(-100f, 100f)
            // Người ngồi phía sau lửa: bong bóng trên đầu; người phía trước: bong bóng dưới chân, để không che ai.
            val above = y < 20f
            val tail: @Composable () -> Unit = {
                Canvas(Modifier.offset(x = (x - bx).dp).size(14.dp, 7.dp)) {
                    val p = if (above) Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }
                    else Path().apply { moveTo(0f, size.height); lineTo(size.width, size.height); lineTo(size.width / 2, 0f); close() }
                    drawPath(p, Ink.copy(alpha = 0.95f))
                }
            }
            val by = if (above) y - avatar.value * 0.62f - 30f else y + avatar.value * 0.62f + 30f
            Column(Modifier.offset(bx.dp, by.dp).zIndex(2000f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!above) tail()
                Text(
                    text, color = CaveBlack, fontSize = 12.sp, lineHeight = 15.sp, textAlign = TextAlign.Center, maxLines = 3,
                    modifier = Modifier.widthIn(max = 150.dp).clip(RoundedCornerShape(14.dp)).background(Ink.copy(alpha = 0.95f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                if (above) tail()
            }
        }
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
