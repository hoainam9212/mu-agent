package vn.vaohang.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.vaohang.ApiException
import vn.vaohang.CaveView
import vn.vaohang.GroupDto
import vn.vaohang.MatchDto
import vn.vaohang.VaoHangApp
import vn.vaohang.core.WorkHours
import java.time.LocalTime

sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data class Cave(val caveId: Long) : Screen
    data object Celebrate : Screen
}

/** Những việc màn hình gọi được; tách khỏi ViewModel để vẽ thử màn hình với dữ liệu giả. */
interface UiActions {
    fun home()
    fun refreshGroups()
    fun createGroup(name: String)
    fun joinGroup(code: String)
    fun approve(groupId: Long, userId: Long)
    fun openCave(caveId: Long)
    fun enterCave(caveId: Long)
    fun refreshCaveQuietly(caveId: Long)
    fun pat(caveId: Long, lineId: Long)
    fun invite(caveId: Long)
    fun leaveCave(caveId: Long)
}

data class UiState(
    val screen: Screen,
    val groups: List<GroupDto> = emptyList(),
    val cave: CaveView? = null,
    val message: String? = null,
    val busy: Boolean = false,
    val nickname: String? = null,
    val meId: Long = 0,
    val nextMatch: MatchDto? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application), UiActions {
    private val app = application as VaoHangApp
    private val api = app.api
    private val prefs = app.prefs

    private val _state = MutableStateFlow(
        UiState(
            screen = if (prefs.token == null) Screen.Onboarding else Screen.Home,
            nickname = prefs.nickname,
            meId = prefs.userId,
        ),
    )
    val state: StateFlow<UiState> = _state

    init {
        if (prefs.token != null) refreshGroups()
    }

    private fun launchTask(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                block()
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.friendly) }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Không kết nối được máy chủ.") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun register(nickname: String, measureConsent: Boolean, worksDaytime: Boolean) = launchTask {
        val created = api.createUser(nickname.trim(), prefs.fcmToken)
        prefs.token = created.token
        prefs.userId = created.userId
        prefs.nickname = nickname.trim()
        prefs.measureConsent = measureConsent
        prefs.workHours = if (worksDaytime) WorkHours(LocalTime.of(8, 0), LocalTime.of(18, 0)) else null
        app.refreshFcmToken()
        _state.update { it.copy(screen = Screen.Home, nickname = prefs.nickname, meId = prefs.userId) }
        loadGroups()
    }

    private suspend fun loadGroups() {
        val groups = api.groups()
        // Trận tới chỉ để hiển thị: lỗi thì bỏ qua, không chặn danh sách hội.
        val next = runCatching { api.nextMatch() }.getOrNull()
        _state.update { it.copy(groups = groups, nextMatch = next) }
    }

    override fun refreshGroups() = launchTask { loadGroups() }

    override fun createGroup(name: String) = launchTask {
        api.createGroup(name)
        loadGroups()
    }

    override fun joinGroup(code: String) = launchTask {
        api.joinGroup(code)
        loadGroups()
        _state.update { it.copy(message = "Đã gửi xin vào hội. Đợi chủ hội duyệt nhé.") }
    }

    override fun approve(groupId: Long, userId: Long) = launchTask {
        api.approve(groupId, userId)
        loadGroups()
    }

    /** Mở từ thông báo: "enter" thì vào hang luôn (người dùng đã bấm Vào hang), còn lại chỉ xem. */
    fun openFromNotification(caveId: Long, screen: String, source: String) {
        prefs.touch()
        when (screen) {
            "celebrate" -> {
                _state.update { it.copy(screen = Screen.Celebrate) }
                viewModelScope.launch { api.telemetry("exit_celebration_opened") }
            }
            "enter" -> enterCaveFrom(caveId, source)
            else -> openCave(caveId)
        }
    }

    override fun openCave(caveId: Long) = launchTask {
        val cave = api.cave(caveId)
        _state.update { it.copy(screen = Screen.Cave(caveId), cave = cave) }
    }

    /** Tải lại màn hang định kỳ để thấy anh em mới vào và câu vỗ vai mới; lỗi mạng thì im lặng. */
    override fun refreshCaveQuietly(caveId: Long) {
        viewModelScope.launch {
            val cave = runCatching { api.cave(caveId) }.getOrNull() ?: return@launch
            _state.update { if (it.screen == Screen.Cave(caveId)) it.copy(cave = cave) else it }
        }
    }

    override fun enterCave(caveId: Long) = enterCaveFrom(caveId, "app")

    private fun enterCaveFrom(caveId: Long, source: String) = launchTask {
        val cave = api.enter(caveId, source)
        _state.update { it.copy(screen = Screen.Cave(caveId), cave = cave) }
    }

    override fun leaveCave(caveId: Long) = launchTask {
        api.leave(caveId)
        _state.update { it.copy(screen = Screen.Home, cave = null) }
        loadGroups()
    }

    override fun pat(caveId: Long, lineId: Long) = launchTask {
        val cave = api.pat(caveId, lineId)
        _state.update { it.copy(cave = cave) }
    }

    override fun invite(caveId: Long) = launchTask {
        val n = api.invite(caveId)
        _state.update {
            it.copy(message = if (n == 0) "Anh em ngoài hang đều đang ngủ cả rồi." else "Đã rủ $n anh em đang thức.")
        }
    }

    override fun home() {
        _state.update { it.copy(screen = Screen.Home, cave = null) }
        refreshGroups()
    }
}
