package vn.vaohang

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable data class UserCreated(@SerialName("user_id") val userId: Long, val token: String)

@Serializable data class Member(val id: Long, val nickname: String)

@Serializable data class CaveRef(val id: Long, val status: String)

@Serializable
data class GroupDto(
    val id: Long,
    val name: String,
    val status: String,
    @SerialName("is_owner") val isOwner: Boolean,
    val members: List<Member> = emptyList(),
    val cave: CaveRef? = null,
    @SerialName("invite_code") val inviteCode: String? = null,
    val pending: List<Member> = emptyList(),
)

@Serializable data class Lights(val on: Boolean, val until: String? = null, @SerialName("fixture_id") val fixtureId: Long? = null)

@Serializable data class PatDto(val from: String, val text: String, val at: String)

@Serializable data class Choice(val id: Long, val text: String)

@Serializable
data class CaveView(
    val id: Long,
    @SerialName("group_id") val groupId: Long,
    val status: String,
    @SerialName("me_inside") val meInside: Boolean,
    val inside: List<Member>,
    val lights: Lights,
    val pats: List<PatDto> = emptyList(),
    val choices: List<Choice> = emptyList(),
    val score: String? = null,
)

@Serializable
data class MatchDto(
    val id: Long,
    val kickoff: String,
    val opponent: String,
    val competition: String,
    val home: Boolean,
    val status: String,
)

class ApiException(val code: Int, val detail: String) : Exception(detail) {
    /** Lời giải thích cho người dùng. */
    val friendly: String
        get() = when (detail) {
            "lights_off" -> "Lửa tắt rồi. Trận sau anh em nhóm lửa tiếp."
            "cave_closed" -> "Hang đã đóng – MU thắng rồi!"
            "already_invited" -> "Trận này bạn rủ anh em rồi."
            "too_many_pats" -> "Zô đủ rồi, đi ngủ thôi!"
            "not_inside" -> "Vào hang trước đã."
            "bad_code" -> "Mã mời không đúng."
            "group_full" -> "Hội đã đủ 10 người."
            else -> "Có lỗi ($code). Thử lại sau."
        }
}

class Api(private val baseUrl: String, private val prefs: Prefs) {
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()

    private suspend fun call(method: String, path: String, body: JsonObject? = null): String =
        withContext(Dispatchers.IO) {
            val payload = (body ?: if (method == "GET" || method == "DELETE") null else JsonObject(emptyMap()))
                ?.toString()?.toRequestBody(mediaType)
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + path)
                .method(method, payload)
                .apply { prefs.token?.let { header("Authorization", "Bearer $it") } }
                .build()
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val detail = runCatching {
                        json.parseToJsonElement(text).let { (it as JsonObject)["detail"]!!.jsonPrimitive.content }
                    }.getOrDefault(text)
                    throw ApiException(response.code, detail)
                }
                text
            }
        }

    suspend fun createUser(nickname: String, fcmToken: String?): UserCreated =
        json.decodeFromString(
            call("POST", "/v1/users", buildJsonObject {
                put("nickname", nickname)
                fcmToken?.let { put("fcm_token", it) }
            }),
        )

    suspend fun updateFcmToken(token: String) {
        call("PATCH", "/v1/me", buildJsonObject { put("fcm_token", token) })
    }

    suspend fun groups(): List<GroupDto> = json.decodeFromString(call("GET", "/v1/groups"))

    suspend fun createGroup(name: String) {
        call("POST", "/v1/groups", buildJsonObject { put("name", name) })
    }

    suspend fun joinGroup(code: String) {
        call("POST", "/v1/groups/join", buildJsonObject { put("invite_code", code.trim().uppercase()) })
    }

    suspend fun approve(groupId: Long, userId: Long) {
        call("POST", "/v1/groups/$groupId/members/$userId/approve")
    }

    suspend fun nextMatch(): MatchDto? =
        json.decodeFromString(MatchDto.serializer().nullable, call("GET", "/v1/matches/next"))

    suspend fun cave(id: Long): CaveView = json.decodeFromString(call("GET", "/v1/caves/$id"))

    suspend fun enter(id: Long, source: String): CaveView =
        json.decodeFromString(call("POST", "/v1/caves/$id/enter", buildJsonObject { put("source", source) }))

    suspend fun leave(id: Long) {
        call("POST", "/v1/caves/$id/leave")
    }

    suspend fun invite(id: Long): Int =
        json.parseToJsonElement(call("POST", "/v1/caves/$id/invite"))
            .let { (it as JsonObject)["recipients"]!!.jsonPrimitive.content.toInt() }

    suspend fun pat(id: Long, lineId: Long): CaveView =
        json.decodeFromString(call("POST", "/v1/caves/$id/pats", buildJsonObject { put("line_id", lineId) }))

    suspend fun presence(fixtureId: Long, state: String) {
        call("POST", "/v1/presence", buildJsonObject {
            put("fixture_id", fixtureId)
            put("state", state)
        })
    }

    /** Chỉ gửi loại sự kiện và vài con số, không bao giờ gửi nội dung. */
    suspend fun telemetry(type: String, props: Map<String, String> = emptyMap()) {
        runCatching {
            call("POST", "/v1/telemetry", buildJsonObject {
                put("events", buildJsonArray {
                    add(buildJsonObject {
                        put("type", type)
                        putJsonObject("props") { props.forEach { (k, v) -> put(k, v) } }
                    })
                })
            })
        }
    }
}
