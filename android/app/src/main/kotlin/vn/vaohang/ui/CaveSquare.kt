package vn.vaohang.ui

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.vaohang.Member
import java.io.File

// Ảnh vuông kiểu Instagram: dựng ở khung 400dp (gần bằng bề ngang điện thoại, cảnh hang giữ đúng tỉ lệ), lưu ra 1080px.
private val SquareSide = 400.dp
private const val SQUARE_PX = 1080
private const val SQUARE_FIRE_Y = 0.62f
private val SquareScene = 330.dp

// Ảnh Gemini (trong assets/square/): vẽ sẵn cả cảnh (`square_lit_N`, `square_sleep_N`) cho hội đông, khó nhận ra ai với ai.
// Hội nhỏ thì Gemini chỉ vẽ nền (`square_bg_lit`, `square_bg_out`), app xếp đúng nhân vật của từng người vào;
// có ảnh vẽ sẵn thì thỉnh thoảng dùng ảnh đó (vui hơn nhưng nhân vật không khớp từng người).
private const val FULL_SCENE_FROM = 7

// Gốc đống lửa trên ảnh nền vuông (tỉ lệ x, y) và vùng sàn ngồi/nằm được. Đo lại khi có ảnh nền thật.
private const val BG_FIRE_X = 0.5f
private const val BG_FIRE_Y = 0.62f
private val BG_FLOOR = floatArrayOf(0.05f, 0.42f, 0.95f, 0.42f, 0.99f, 1.0f, 0.01f, 1.0f)

/** Ảnh Gemini trong assets/square/, null nếu chưa có (ảnh thêm dần, thiếu thì dùng cảnh ghép lớp). */
private fun squareArt(context: Context, name: String): ImageBitmap? = runCatching {
    context.assets.open("square/$name.webp").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
}.getOrNull()

private fun sceneName(n: Int, burning: Boolean) = "square_${if (burning) "lit" else "sleep"}_$n"

/** Hội nhỏ có ảnh vẽ sẵn cả cảnh: được chọn ngẫu nhiên giữa ảnh đó và cảnh nhân vật thật. */
fun canSwitchSquare(context: Context, n: Int, burning: Boolean): Boolean =
    n < FULL_SCENE_FROM && runCatching { context.assets.open("square/${sceneName(n, burning)}.webp").close() }.isSuccess

/**
 * Cảnh hang vuông để khoe: chỉ những người đang trong hang, không tên, không chữ, không ai được tô sáng.
 * Lửa cháy thì cả hội ngồi quanh lửa; lửa tắt thì cả hội nằm ngủ. [painted]: hội nhỏ cũng dùng ảnh vẽ sẵn nếu có.
 */
@Composable
fun CaveSquare(people: List<Member>, burning: Boolean, modifier: Modifier = Modifier, painted: Boolean = false) {
    val context = LocalContext.current
    val n = people.size
    val scene = remember(n, burning, painted) {
        if (n >= FULL_SCENE_FROM || painted) squareArt(context, sceneName(n, burning)) else null
    }
    val bg = remember(burning) { squareArt(context, if (burning) "square_bg_lit" else "square_bg_out") }
    when {
        scene != null -> Image(scene, null, modifier.requiredSize(SquareSide), contentScale = ContentScale.Crop)
        bg != null -> Box(modifier.requiredSize(SquareSide)) {
            Image(bg, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            val side = SquareSide.value
            val floor = remember {
                { ox: Float, oy: Float -> inFloor((BG_FIRE_X * side + ox) / side, (BG_FIRE_Y * side + oy) / side, BG_FLOOR) }
            }
            CampScene(
                people, meId = Long.MIN_VALUE, burning, height = SquareScene, onFloor = floor, showFire = false,
                modifier = Modifier.align(Alignment.TopCenter).offset(
                    x = SquareSide * (BG_FIRE_X - 0.5f),
                    y = SquareSide * BG_FIRE_Y - SquareScene / 2 - SquareScene * 0.19f,
                ),
            )
        }
        else -> CaveBackdrop(fireOn = burning, fireY = SQUARE_FIRE_Y, shadeEdges = false, modifier = modifier.requiredSize(SquareSide)) {
            val floor = remember { caveFloorTest(SquareSide.value, SquareSide.value, SQUARE_FIRE_Y) }
            CampScene(
                people, meId = Long.MIN_VALUE, burning, height = SquareScene, onFloor = floor,
                modifier = Modifier.align(Alignment.TopCenter)
                    .offset(y = SquareSide * SQUARE_FIRE_Y - SquareScene / 2 - SquareScene * 0.19f),
            )
        }
    }
}

/** [CaveSquare] thu vừa bề ngang chỗ đặt; [layer] (nếu có) ghi lại đúng khung 400dp để xuất ảnh. */
@Composable
fun FittedCaveSquare(
    people: List<Member>, burning: Boolean, modifier: Modifier = Modifier, layer: GraphicsLayer? = null, painted: Boolean = false,
) {
    BoxWithConstraints(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        val s = maxWidth / SquareSide
        Box(
            Modifier.requiredSize(SquareSide)
                .graphicsLayer { scaleX = s; scaleY = s }
                .drawWithContent {
                    if (layer == null) drawContent() else {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                },
        ) {
            CaveSquare(people, burning, painted = painted)
        }
    }
}

/** Ảnh 1080x1080 từ lớp đã ghi. */
suspend fun squareBitmap(layer: GraphicsLayer): Bitmap {
    val raw = layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    return Bitmap.createScaledBitmap(raw, SQUARE_PX, SQUARE_PX, true)
}

/** Mở bảng chia sẻ của Android (Instagram, Messenger, Zalo…) với ảnh PNG trong thư mục cache của app. */
suspend fun shareSquare(context: Context, bitmap: Bitmap) {
    val file = withContext(Dispatchers.IO) {
        File(context.cacheDir, "shared").apply { mkdirs() }.resolve("vao-hang.png").also { f ->
            f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

/** Lưu được thẳng vào thư viện ảnh từ Android 10 (không cần xin quyền); máy cũ hơn thì dùng chia sẻ. */
val canSaveSquare: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

suspend fun saveSquare(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "vao-hang-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Vào Hang")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
    runCatching {
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    }.onFailure { resolver.delete(uri, null, null) }.isSuccess
}
