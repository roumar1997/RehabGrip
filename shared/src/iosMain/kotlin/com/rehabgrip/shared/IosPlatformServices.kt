package com.rehabgrip.shared

import com.rehabgrip.core.domain.port.CuePlayer
import com.rehabgrip.core.domain.port.ReminderScheduler
import com.rehabgrip.core.domain.reminder.Reminder
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.Foundation.NSDateComponents
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/** System sounds + haptics (beeps follow the silent switch, haptics always work). */
internal class IosCuePlayer : CuePlayer {
    private val light = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val heavy = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    private val notice = UINotificationFeedbackGenerator()

    override fun countdownTick() {
        AudioServicesPlaySystemSound(SOUND_TICK)
        light.impactOccurred()
    }

    override fun pullStart() {
        AudioServicesPlaySystemSound(SOUND_START)
        heavy.impactOccurred()
    }

    override fun pullEnd() {
        AudioServicesPlaySystemSound(SOUND_END)
        light.impactOccurred()
    }

    override fun aboveMax() {
        AudioServicesPlaySystemSound(SOUND_ALARM)
        notice.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
    }

    override fun belowMin() = light.impactOccurred()

    override fun finished() {
        AudioServicesPlaySystemSound(SOUND_DONE)
        notice.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
    }

    private companion object {
        const val SOUND_TICK = 1103u
        const val SOUND_START = 1113u
        const val SOUND_END = 1114u
        const val SOUND_ALARM = 1005u
        const val SOUND_DONE = 1025u
    }
}

/** One repeating calendar trigger per selected weekday. */
internal class IosReminderScheduler : ReminderScheduler {
    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun reschedule(reminders: List<Reminder>) {
        center.removeAllPendingNotificationRequests()
        reminders.filter { it.enabled }.forEach { r ->
            r.days.forEach { day ->
                val components = NSDateComponents().apply {
                    // Apple weekday: 1 = Sunday ... 7 = Saturday; kotlinx: MONDAY.ordinal = 0.
                    weekday = ((day.ordinal + 1) % 7 + 1).toLong()
                    hour = r.hour.toLong()
                    minute = r.minute.toLong()
                }
                val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = true)
                center.addNotificationRequest(
                    UNNotificationRequest.requestWithIdentifier("reminder-${r.id}-${day.ordinal}", content(BODY), trigger),
                    withCompletionHandler = null,
                )
            }
        }
    }

    override suspend fun ensurePermission(): Boolean = suspendCancellableCoroutine { cont ->
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, _ -> cont.resume(granted) }
    }

    override fun showTest() {
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, repeats = false)
        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier("reminder-test", content("Así te llegarán los recordatorios de Rehagrip."), trigger),
            withCompletionHandler = null,
        )
    }

    private fun content(body: String) = UNMutableNotificationContent().apply {
        setTitle("Hora de entrenar el agarre")
        setBody(body)
        setSound(UNNotificationSound.defaultSound)
    }

    private companion object {
        const val BODY = "Mide tu fuerza o haz tu sesión guiada de hoy."
    }
}
