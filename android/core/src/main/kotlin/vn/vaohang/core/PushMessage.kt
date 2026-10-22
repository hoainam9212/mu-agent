package vn.vaohang.core

import kotlinx.serialization.json.Json
import java.time.Instant

/** FCM data message từ máy chủ (xem server/vaohang/services/cave.py). */
sealed interface PushMessage {
    data class LightsOn(
        val caveId: Long,
        val groupName: String,
        val fixtureId: Long,
        val result: String,
        val score: String,
        val lightsUntil: Instant,
        val youInside: Boolean,
        val insideNames: List<String>,
    ) : PushMessage

    data class Invite(val caveId: Long, val fromName: String) : PushMessage

    data class PatReceived(val caveId: Long, val fromName: String, val text: String) : PushMessage

    data class Exit(val caveId: Long, val score: String) : PushMessage

    data class MatchReminder(val fixtureId: Long, val kickoff: Instant, val opponent: String) : PushMessage

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(data: Map<String, String>): PushMessage? = runCatching {
            when (data["kind"]) {
                "cave_lights_on" -> LightsOn(
                    caveId = data.getValue("cave_id").toLong(),
                    groupName = data["group_name"].orEmpty(),
                    fixtureId = data.getValue("fixture_id").toLong(),
                    result = data.getValue("result"),
                    score = data["score"].orEmpty(),
                    lightsUntil = Instant.parse(data.getValue("lights_until")),
                    youInside = data["you_inside"] == "1",
                    insideNames = data["inside_names"]?.let { json.decodeFromString<List<String>>(it) }.orEmpty(),
                )
                "cave_invite" -> Invite(data.getValue("cave_id").toLong(), data.getValue("from_name"))
                "cave_pat" -> PatReceived(
                    data.getValue("cave_id").toLong(), data.getValue("from_name"), data.getValue("text"),
                )
                "cave_exit" -> Exit(data.getValue("cave_id").toLong(), data["score"].orEmpty())
                "match_reminder" -> MatchReminder(
                    data.getValue("fixture_id").toLong(), Instant.parse(data.getValue("kickoff")),
                    data["opponent"].orEmpty(),
                )
                else -> null
            }
        }.getOrNull()
    }
}

enum class Delivery { NOW, DEFERRED, DROP }

object DeliveryRules {
    /** Tin hang/ra hang: thức thì báo ngay, không thì để sau. Lời mời và vỗ vai chỉ có nghĩa lúc đang thức. */
    fun decide(message: PushMessage, presence: Presence): Delivery = when (message) {
        is PushMessage.LightsOn, is PushMessage.Exit ->
            if (presence == Presence.AWAKE) Delivery.NOW else Delivery.DEFERRED
        is PushMessage.Invite, is PushMessage.PatReceived ->
            if (presence == Presence.AWAKE) Delivery.NOW else Delivery.DROP
        is PushMessage.MatchReminder -> Delivery.NOW
    }
}
