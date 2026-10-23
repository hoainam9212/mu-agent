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

class AppViewModel(application: Application) : AndroidViewModel(application) {
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

    fun refreshGroups() = launchTask { loadGroups() }

    fun createGroup(name: String) = launchTask {
        api.createGroup(name)
        loadGroups()
    }

    fun joinGroup(code: String) = launchTask {
        api.joinGroup(code)
        loadGroups()
        _state.update { it.copy(message = "Đã gửi xin vào hội. Đợi chủ hội duyệt nhé.") }
    }

    fun approve(groupId: Long, userId: Long) = launchTask {
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
            "enter" -> enterCave(caveId, source)
            else -> openCave(caveId)
        }
    }

    fun openCave(caveId: Long) = launchTask {
        val cave = api.cave(caveId)
        _state.update { it.copy(screen = Screen.Cave(caveId), cave = cave) }
    }

    /** Tải lại màn hang định kỳ để thấy anh em mới vào và câu vỗ vai mới; lỗi mạng thì im lặng. */
    fun refreshCaveQuietly(caveId: Long) {
        viewModelScope.launch {
            val cave = runCatching { api.cave(caveId) }.getOrNull() ?: return@launch
            _state.update { if (it.screen == Screen.Cave(caveId)) it.copy(cave = cave) else it }
        }
    }

    fun enterCave(caveId: Long, source: String = "app") = launchTask {
        val cave = api.enter(caveId, source)
        _state.update { it.copy(screen = Screen.Cave(caveId), cave = cave) }
    }

    fun leaveCave(caveId: Long) = launchTask {
        api.leave(caveId)
        _state.update { it.copy(screen = Screen.Home, cave = null) }
        loadGroups()
    }

    fun pat(caveId: Long, lineId: Long) = launchTask {
        val cave = api.pat(caveId, lineId)
        _state.update { it.copy(cave = cave) }
    }

    fun invite(caveId: Long) = launchTask {
        val n = api.invite(caveId)
        _state.update {
            it.copy(message = if (n == 0) "Anh em ngoài hang đều đang ngủ cả rồi." else "Đã rủ $n anh em đang thức.")
        }
    }

    fun home() {
        _state.update { it.copy(screen = Screen.Home, cave = null) }
        refreshGroups()
    }
}
