package vn.vaohang.core

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val VN = ZoneId.of("Asia/Ho_Chi_Minh")
private val JP = ZoneId.of("Asia/Tokyo")

private fun signals(
    dnd: Boolean = false,
    screenOn: Boolean = false,
    declined: Boolean = false,
    checkedIn: Boolean = false,
    since: Duration? = null,
    time: LocalTime = LocalTime.of(3, 55),
    work: WorkHours? = null,
) = PresenceSignals(dnd, screenOn, declined, checkedIn, since, time, work)

class PresenceRulesTest {
    @Test fun dndMeansAsleepEvenWithScreenOn() =
        assertEquals(Presence.ASLEEP, PresenceRules.evaluate(signals(dnd = true, screenOn = true)))

    @Test fun declinedTonightMeansAsleep() =
        assertEquals(Presence.ASLEEP, PresenceRules.evaluate(signals(declined = true, screenOn = true)))

    @Test fun screenOnMeansAwake() = assertEquals(Presence.AWAKE, PresenceRules.evaluate(signals(screenOn = true)))

    @Test fun checkedInAndRecentlyUsedMeansAwake() = assertEquals(
        Presence.AWAKE,
        PresenceRules.evaluate(signals(checkedIn = true, since = Duration.ofMinutes(10))),
    )

    @Test fun checkedInButIdleMeansAsleep() = assertEquals(
        Presence.ASLEEP,
        PresenceRules.evaluate(signals(checkedIn = true, since = Duration.ofMinutes(40))),
    )

    @Test fun unknownDefaultsToAsleep() = assertEquals(Presence.ASLEEP, PresenceRules.evaluate(signals()))

    @Test fun workHoursMeansBusy() = assertEquals(
        Presence.BUSY,
        PresenceRules.evaluate(
            signals(screenOn = true, time = LocalTime.of(9, 0), work = WorkHours(LocalTime.of(8, 0), LocalTime.of(18, 0))),
        ),
    )

    @Test fun overnightWorkHours() {
        val night = WorkHours(LocalTime.of(22, 0), LocalTime.of(6, 0))
        assert(night.contains(LocalTime.of(3, 0)))
        assert(!night.contains(LocalTime.of(12, 0)))
    }
}

class DeferredDeliveryTest {
    private val now = ZonedDateTime.of(2026, 11, 4, 3, 55, 0, 0, VN)

    @Test fun usesNextAlarmWhenSoon() {
        val alarm = ZonedDateTime.of(2026, 11, 4, 6, 30, 0, 0, VN).toInstant()
        assertEquals(alarm, DeferredDelivery.at(Presence.ASLEEP, now, alarm, null))
    }

    @Test fun fallsBackToSevenAm() = assertEquals(
        ZonedDateTime.of(2026, 11, 4, 7, 0, 0, 0, VN).toInstant(),
        DeferredDelivery.at(Presence.ASLEEP, now, null, null),
    )

    @Test fun ignoresFarAlarm() {
        val far = now.plusDays(2).toInstant()
        assertEquals(
            ZonedDateTime.of(2026, 11, 4, 7, 0, 0, 0, VN).toInstant(),
            DeferredDelivery.at(Presence.ASLEEP, now, far, null),
        )
    }

    @Test fun busyWaitsForEndOfWorkInJapan() {
        val tokyoMorning = ZonedDateTime.of(2026, 11, 4, 9, 0, 0, 0, JP)
        val work = WorkHours(LocalTime.of(8, 0), LocalTime.of(18, 0))
        assertEquals(
            ZonedDateTime.of(2026, 11, 4, 18, 0, 0, 0, JP).toInstant(),
            DeferredDelivery.at(Presence.BUSY, tokyoMorning, null, work),
        )
    }
}

class LightsOutTest {
    @Test fun matchesProposalExample() {
        val now = ZonedDateTime.of(2026, 10, 14, 4, 25, 0, 0, VN).toInstant()
        val alarm = ZonedDateTime.of(2026, 10, 14, 6, 30, 0, 0, VN).toInstant()
        assertEquals("Báo thức của bạn: 6:30.\nCòn kịp ngủ 2 tiếng.", LightsOut.text(now, alarm, VN))
    }

    @Test fun minutesWhenUnderAnHour() {
        val now = ZonedDateTime.of(2026, 10, 14, 6, 0, 0, 0, VN).toInstant()
        val alarm = ZonedDateTime.of(2026, 10, 14, 6, 45, 0, 0, VN).toInstant()
        assertEquals("Báo thức của bạn: 6:45.\nCòn kịp ngủ 45 phút.", LightsOut.text(now, alarm, VN))
    }

    @Test fun noAlarmNoText() = assertNull(LightsOut.text(Instant.EPOCH, null, VN))
}

class ReminderRuleTest {
    @Test fun romaIsANightMatchInVietnamAndJapan() {
        val kickoff = Instant.parse("2026-11-03T20:00:00Z") // 03:00 VN, 05:00 JP
        assert(ReminderRule.isNightMatch(kickoff, VN))
        assert(ReminderRule.isNightMatch(kickoff, JP))
    }

    @Test fun eveningMatchIsNot() = assert(!ReminderRule.isNightMatch(Instant.parse("2026-11-01T12:00:00Z"), VN))
}

class PushMessageTest {
    private val lightsOn = mapOf(
        "kind" to "cave_lights_on", "cave_id" to "7", "group_name" to "Hội", "fixture_id" to "3",
        "result" to "L", "score" to "Atlético Madrid 2-0 MU", "lights_until" to "2026-10-13T21:25:00.123456Z",
        "you_inside" to "0", "inside_names" to "[\"Tuấn\", \"Hùng\"]",
    )

    @Test fun parsesServerPayload() {
        val m = PushMessage.parse(lightsOn) as PushMessage.LightsOn
        assertEquals(7, m.caveId)
        assertEquals(listOf("Tuấn", "Hùng"), m.insideNames)
        assertEquals(false, m.youInside)
    }

    @Test fun unknownOrBrokenPayloadIsIgnored() {
        assertNull(PushMessage.parse(mapOf("kind" to "nope")))
        assertNull(PushMessage.parse(mapOf("kind" to "cave_invite")))
    }

    @Test fun deliveryRules() {
        val m = PushMessage.parse(lightsOn)!!
        assertEquals(Delivery.NOW, DeliveryRules.decide(m, Presence.AWAKE))
        assertEquals(Delivery.DEFERRED, DeliveryRules.decide(m, Presence.ASLEEP))
        assertEquals(Delivery.DROP, DeliveryRules.decide(PushMessage.Invite(7, "Tuấn"), Presence.BUSY))
    }
}

class NotificationCopyTest {
    private fun lights(inside: Boolean, names: List<String>, result: String = "L") = PushMessage.LightsOn(
        7, "Hội", 3, result, "Atlético Madrid 2-0 MU", Instant.EPOCH, inside, names,
    )

    @Test fun outsiderSeesOnlyWhoIsInside() = assertEquals(
        Copy("Hang đang mở", "Atlético Madrid 2-0 MU. Tuấn, Hùng và Long đang trong hang."),
        NotificationCopy.lightsOn(lights(false, listOf("Tuấn", "Hùng", "Long")), deferred = false),
    )

    @Test fun morningVersion() = assertEquals(
        "Đêm qua Atlético Madrid 2-0 MU. Chưa ai vào hang.",
        NotificationCopy.lightsOn(lights(false, emptyList()), deferred = true).body,
    )

    @Test fun drawTitle() =
        assertEquals("Hang sáng đèn lại", NotificationCopy.lightsOn(lights(true, emptyList(), "D"), false).title)

    @Test fun reminderUsesLocalTime() = assertEquals(
        "Bóng lăn lúc 03:00. Đêm nay bạn xem không?",
        NotificationCopy.reminder(PushMessage.MatchReminder(1, Instant.parse("2026-11-03T20:00:00Z"), "Roma"), VN).body,
    )
}
