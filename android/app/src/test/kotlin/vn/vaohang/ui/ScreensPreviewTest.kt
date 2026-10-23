package vn.vaohang.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.runtime.Composable
import org.junit.Rule
import org.junit.Test
import vn.vaohang.CaveRef
import vn.vaohang.CaveView
import vn.vaohang.Choice
import vn.vaohang.GroupDto
import vn.vaohang.Lights
import vn.vaohang.MatchDto
import vn.vaohang.Member
import vn.vaohang.PatDto
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Ảnh chụp các màn hình với dữ liệu giả, để xem giao diện trước khi cài: ./gradlew :app:recordPaparazziDebug */
class ScreensPreviewTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.NoActionBar")

    private object NoActions : UiActions {
        override fun home() {}
        override fun refreshGroups() {}
        override fun createGroup(name: String) {}
        override fun joinGroup(code: String) {}
        override fun approve(groupId: Long, userId: Long) {}
        override fun openCave(caveId: Long) {}
        override fun enterCave(caveId: Long) {}
        override fun refreshCaveQuietly(caveId: Long) {}
        override fun pat(caveId: Long, lineId: Long) {}
        override fun invite(caveId: Long) {}
        override fun leaveCave(caveId: Long) {}
    }

    private val me = Member(1, "Nam")
    private val crew = listOf(me, Member(2, "Tuấn"), Member(3, "Hùng"), Member(4, "Long"), Member(5, "Đức"))
    private val pats = listOf(
        PatDto("Tuấn", "Thua trận chứ không thua tình anh em. Vỗ vai cái rồi đi ngủ.", "2026-10-23T03:00:00Z"),
        PatDto("Hùng", "Ít ra đêm nay mình không phải thức một mình.", "2026-10-23T03:01:00Z"),
    )
    private val choices = listOf(
        Choice(1, "Cay thì cay, nhưng mai vẫn phải đi làm. Ngủ sớm cho đỡ cay."),
        Choice(2, "Fan MU mình lì đòn nhất hệ mặt trời rồi, đêm nay chỉ là tập thêm."),
        Choice(3, "Còn cả mùa giải phía trước, đâu phải chung kết đâu."),
    )

    private fun cave(fireOn: Boolean, meInside: Boolean = true) = CaveView(
        id = 7, groupId = 1, status = "OPEN", meInside = meInside,
        inside = if (meInside) crew else crew.drop(1),
        lights = Lights(on = fireOn, until = Instant.now().plus(if (fireOn) 23 else -5, ChronoUnit.MINUTES).toString()),
        pats = if (meInside) pats else emptyList(),
        choices = if (meInside && fireOn) choices else emptyList(),
        score = "Atlético Madrid 2-0 MU",
    )

    private fun shot(content: @Composable () -> Unit) = paparazzi.snapshot { VaoHangTheme(content) }

    @Test fun onboarding() = shot { OnboardingScreen(busy = false) { _, _, _ -> } }

    @Test fun home() = shot {
        val groups = listOf(
            GroupDto(
                1, "Hội Tối Cổ Tokyo", "approved", isOwner = true, members = crew, cave = CaveRef(7, "OPEN"),
                inviteCode = "QXGEGT", pending = listOf(Member(6, "Phong")),
            ),
            GroupDto(2, "Hội cấp 3", "approved", isOwner = false, members = crew.take(3)),
        )
        val next = MatchDto(2, Instant.now().plus(2, ChronoUnit.DAYS).toString(), "Bournemouth", "Premier League", home = true, status = "SCHEDULED")
        HomeScreen(UiState(Screen.Home, groups = groups, nickname = "Nam", meId = 1, nextMatch = next), NoActions)
    }

    @Test fun caveFireBurning() = shot { CaveScreen(cave(fireOn = true), "Hội Tối Cổ Tokyo", 1, NoActions) }

    @Test fun caveFireOut() = shot { CaveScreen(cave(fireOn = false), "Hội Tối Cổ Tokyo", 1, NoActions) }

    @Test fun caveNotInside() = shot { CaveScreen(cave(fireOn = true, meInside = false), "Hội Tối Cổ Tokyo", 1, NoActions) }

    @Test fun celebrate() = shot { CelebrateScreen {} }
}
