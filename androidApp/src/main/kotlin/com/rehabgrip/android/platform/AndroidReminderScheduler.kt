package com.rehabgrip.android.platform

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rehabgrip.android.MainActivity
import com.rehabgrip.android.R
import com.rehabgrip.core.data.DriverFactory
import com.rehabgrip.core.data.SqlReminderRepository
import com.rehabgrip.core.data.createDatabase
import com.rehabgrip.core.domain.port.ReminderScheduler
import com.rehabgrip.core.domain.reminder.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * Weekly reminders with AlarmManager (inexact 1-minute window: no exact-alarm
 * permission needed). Each alarm shows the notification and schedules the next one.
 */
class AndroidReminderScheduler(
    private val context: Context,
    private val requestPermission: suspend () -> Boolean,
) : ReminderScheduler {

    override suspend fun reschedule(reminders: List<Reminder>) = schedule(context, reminders)

    override suspend fun ensurePermission(): Boolean = requestPermission()

    override fun showTest() = notify(context, TEST_ID.toInt(), "Así te llegarán los recordatorios de Rehagrip.")

    companion object {
        private const val CHANNEL = "reminders"
        private const val PREFS = "reminder_alarms"
        private const val TEST_ID = 999_999L
        private const val WINDOW_MS = 60_000L
        const val EXTRA_ID = "reminder_id"

        fun schedule(context: Context, reminders: List<Reminder>) {
            val alarms = context.getSystemService(AlarmManager::class.java)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getStringSet("ids", emptySet()).orEmpty().forEach { alarms.cancel(pending(context, it.toLong())) }
            val now = System.currentTimeMillis()
            val scheduled = reminders.filter { it.enabled }.mapNotNull { r ->
                val at = r.nextTriggerMillis(now, TimeZone.currentSystemDefault()) ?: return@mapNotNull null
                alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, pending(context, r.id))
                r.id.toString()
            }
            prefs.edit().putStringSet("ids", scheduled.toSet()).apply()
        }

        private fun pending(context: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
            context, id.toInt(),
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun notify(context: Context, id: Int, text: String) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Recordatorios", NotificationManager.IMPORTANCE_HIGH))
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Hora de entrenar el agarre")
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
        }

        /** Reads the reminders straight from the database (receiver has no UI container). */
        fun rescheduleFromDb(context: Context, onDone: () -> Unit) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = SqlReminderRepository(createDatabase(DriverFactory(context.applicationContext)))
                    schedule(context, repo.all())
                } finally {
                    onDone()
                }
            }
        }
    }
}

/** Fires the notification and plans the following week day. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AndroidReminderScheduler.EXTRA_ID, 0L)
        AndroidReminderScheduler.notify(context, id.toInt(), "Mide tu fuerza o haz tu sesión guiada de hoy.")
        val pending = goAsync()
        AndroidReminderScheduler.rescheduleFromDb(context) { pending.finish() }
    }
}

/** Alarms are lost on reboot: plan them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        AndroidReminderScheduler.rescheduleFromDb(context) { pending.finish() }
    }
}
