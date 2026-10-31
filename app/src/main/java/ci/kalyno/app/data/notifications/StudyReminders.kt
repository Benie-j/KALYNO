package ci.kalyno.app.data.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ci.kalyno.app.R
import ci.kalyno.app.data.AppPrefs
import ci.kalyno.app.data.dashboard.DashboardRepository
import ci.kalyno.app.data.dashboard.MissionStatus
import ci.kalyno.app.data.dashboard.StudentId
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Rappels système (l'appli peut être fermée) : cartes à découvrir dans le Dojo et révisions urgentes FSRS.
 *
 * Une tâche WorkManager périodique (survit au redémarrage du téléphone) relance le calcul du tableau de bord
 * ([DashboardRepository.load]) : les chiffres notifiés sont donc exactement ceux que l'élève verra dans le Dojo.
 * Anti-spam : une notification de chaque type par jour au maximum, et aucune la nuit.
 */
object StudyReminders {

    private const val WORK_NAME = "kalyno-study-reminders"
    private const val PERIOD_HOURS = 3L

    internal const val EXTRA_OPEN_DOJO = "ci.kalyno.app.OPEN_DOJO"

    /** Passe à vrai quand l'élève touche une notification ; MainScreen ouvre alors l'onglet Dojo puis le remet à faux. */
    val openDojoRequested = mutableStateOf(false)

    internal const val CHANNEL_URGENT = "kalyno_urgent_reviews"
    internal const val CHANNEL_DOJO = "kalyno_dojo_new_cards"
    internal const val ID_URGENT = 4101
    internal const val ID_DOJO = 4102

    /** Heures de silence : de 21 h à 7 h (heure du téléphone), rien n'est envoyé. */
    internal const val QUIET_FROM_HOUR = 21
    internal const val QUIET_UNTIL_HOUR = 7

    /** Heures d'envoi (heure du téléphone) : pause de midi et sortie des cours. Modifiable ici. */
    internal val SLOTS = listOf(LocalTime.of(12, 30), LocalTime.of(17, 30))

    private const val OLD_WORK_NAME = "kalyno-study-reminders"
    internal const val TAG = "KalynoReminders"

    /** À appeler au lancement de l'appli : sans effet si les créneaux sont déjà programmés. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        createChannels(app)
        val manager = WorkManager.getInstance(app)
        // L'ancienne cadence (toutes les 3 h, heure aléatoire) est remplacée par des créneaux fixes
        manager.cancelUniqueWork(OLD_WORK_NAME)
        SLOTS.forEach { slot ->
            val request = PeriodicWorkRequestBuilder<StudyReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayUntil(slot), TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            manager.enqueueUniquePeriodicWork(
                "kalyno-reminder-${slot.hour}h${slot.minute}",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    /** Millisecondes jusqu'à la prochaine occurrence de [slot] (aujourd'hui si pas encore passée, sinon demain). */
    internal fun delayUntil(slot: LocalTime, now: LocalDateTime = LocalDateTime.now()): Long {
        var next = now.toLocalDate().atTime(slot)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }

    internal fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_URGENT, "Révisions urgentes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alerte quand des notions risquent d'être oubliées."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DOJO, "Nouvelles cartes du Dojo", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alerte quand de nouvelles cartes t'attendent dans le Dojo."
            }
        )
    }

    internal fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    internal fun isQuietHour(now: LocalTime = LocalTime.now()): Boolean =
        now.hour >= QUIET_FROM_HOUR || now.hour < QUIET_UNTIL_HOUR

    @SuppressLint("MissingPermission") // vérifié par canNotify() avant tout appel
    internal fun post(context: Context, id: Int, channel: String, title: String, text: String): Boolean {
        if (!canNotify(context)) return false
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_DOJO, true)
            }
        val tap = launch?.let {
            PendingIntent.getActivity(context, id, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setColor(ContextCompat.getColor(context, R.color.kalyno_blue))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }
}

class StudyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val state = context.applicationContext.getSharedPreferences("kalyno_reminders", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        val context = applicationContext
        val levelId = AppPrefs(context).level
        if (levelId == null) {
            Log.i(StudyReminders.TAG, "Ignoré : classe pas encore choisie")
            return Result.success()
        }
        if (StudyReminders.isQuietHour()) {
            Log.i(StudyReminders.TAG, "Ignoré : heures de silence")
            return Result.success() // rien n'est marqué : le prochain passage notifiera
        }
        if (!StudyReminders.canNotify(context)) {
            // Autorisation refusée : inutile de calculer, et surtout ne rien marquer comme « envoyé »
            Log.w(StudyReminders.TAG, "Ignoré : notifications non autorisées pour KALYNO")
            return Result.success()
        }

        val snapshot = try {
            DashboardRepository(context).load(levelId, StudentId.get(context))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }

        StudyReminders.createChannels(context)
        val today = LocalDate.now().toEpochDay()
        val manager = NotificationManagerCompat.from(context)

        // ----- Révisions urgentes (cartes dues selon FSRS) -----
        val urgent = snapshot.urgentTotal
        if (urgent <= 0) {
            manager.cancel(StudyReminders.ID_URGENT) // plus rien d'urgent : on retire l'alerte périmée
        } else if (state.getLong(KEY_URGENT_DAY, -1L) != today) {
            val topic = snapshot.urgentTopics.firstOrNull()?.label
            val urgentSent = StudyReminders.post(
                context = context,
                id = StudyReminders.ID_URGENT,
                channel = StudyReminders.CHANNEL_URGENT,
                title = "Révisions urgentes",
                text = buildString {
                    append(if (urgent == 1) "1 notion à sauver" else "$urgent notions à sauver")
                    append(" dans le Dojo")
                    if (topic != null) append(" · $topic")
                    append(". Quelques minutes suffisent pour la garder en tête !")
                }
            )
            // Marqué « fait » seulement si la notification est vraiment partie
            if (urgentSent) state.edit().putLong(KEY_URGENT_DAY, today).apply()
            Log.i(StudyReminders.TAG, "Révisions urgentes : $urgent, envoyée = $urgentSent")
        }

        // ----- Nouvelles cartes disponibles (mission du jour) -----
        val mission = snapshot.mission
        if (snapshot.missionStatus == MissionStatus.READY && mission != null && mission.items.isNotEmpty()) {
            val signature = mission.items.map { it.cardId }.hashCode()
            val alreadyKnown = state.getInt(KEY_NEW_SIGNATURE, 0) == signature
            if (!alreadyKnown && state.getLong(KEY_NEW_DAY, -1L) != today) {
                val count = mission.items.size
                val dojoSent = StudyReminders.post(
                    context = context,
                    id = StudyReminders.ID_DOJO,
                    channel = StudyReminders.CHANNEL_DOJO,
                    title = "Nouvelles cartes dans le Dojo",
                    text = (if (count == 1) "1 nouvelle carte t'attend" else "$count nouvelles cartes t'attendent") +
                        ". Lance ta mission du jour !"
                )
                if (dojoSent) state.edit().putLong(KEY_NEW_DAY, today).putInt(KEY_NEW_SIGNATURE, signature).apply()
                Log.i(StudyReminders.TAG, "Nouvelles cartes : $count, envoyée = $dojoSent")
            }
        } else {
            manager.cancel(StudyReminders.ID_DOJO)
        }

        return Result.success()
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val KEY_URGENT_DAY = "urgent_day"
        const val KEY_NEW_DAY = "new_cards_day"
        const val KEY_NEW_SIGNATURE = "new_cards_signature"
    }
}
