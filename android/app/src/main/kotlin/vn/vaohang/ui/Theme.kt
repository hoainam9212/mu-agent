package vn.vaohang.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Bảng màu "trong hang": đá tối, ánh lửa, đỏ – vàng MU.
val CaveBlack = Color(0xFF0B0908)
val CaveDeep = Color(0xFF1A1410)
val Rock = Color(0xFF241D18)
val RockLight = Color(0xFF362C25)
val RockEdge = Color(0xFF4A3D33)
val Ember = Color(0xFFFF9F43)
val Gold = Color(0xFFF2C200)
val MuRed = Color(0xFFDA291C)
val RedDeep = Color(0xFF7A0E10)
val Ink = Color(0xFFF7F1E8)
val Mute = Color(0xFFADA397)
val Moon = Color(0xFFD9E2FF)

private val AvatarColors = listOf(
    Color(0xFFDA291C), Color(0xFFF2C200), Color(0xFF3FA796), Color(0xFF5B7CFA),
    Color(0xFFE07A5F), Color(0xFFB56CE0), Color(0xFF7FB069), Color(0xFFF28482),
)

fun avatarColor(seed: Long): Color = AvatarColors[Math.floorMod(seed, AvatarColors.size.toLong()).toInt()]

/** Chữ trắng trên nền tối, chữ đen trên nền sáng (vàng). */
fun onColor(background: Color): Color = if (background.luminance() > 0.45f) CaveBlack else Color.White

@Composable
fun VaoHangTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MuRed, onPrimary = Color.White,
            secondary = Gold, onSecondary = CaveBlack,
            background = CaveBlack, onBackground = Ink,
            surface = Rock, onSurface = Ink, surfaceVariant = RockLight, onSurfaceVariant = Mute,
            outline = RockEdge,
        ),
        content = content,
    )
}
