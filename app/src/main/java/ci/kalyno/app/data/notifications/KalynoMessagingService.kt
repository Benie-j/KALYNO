package ci.kalyno.app.data.notifications

import android.content.Context
import android.util.Log
import ci.kalyno.app.data.dashboard.KalynoDatabase
import ci.kalyno.app.data.dashboard.StudentId
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Reçoit les notifications push envoyées par le serveur (fonction Supabase notify-content) dès qu'une
 * nouveauté est publiée : nouvelle leçon, nouvelles cartes, nouvelle annonce « À la une ».
 *
 * Les messages ne contiennent que des données : c'est l'appli qui compose le texte, ce qui lui permet
 * de savoir si la leçon est déjà terminée (alors les cartes sont dans le Dojo) et d'ouvrir le bon écran.
 */
class KalynoMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Les abonnements aux sujets suivent l'appareil ; on les vérifie simplement
        PushSetup.sync(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val context = applicationContext
        val data = message.data
        Log.i("KalynoPush", "Push reçu : $data")
        ContentWatcher.createChannel(context)

        when (data["type"]) {
            "lesson" -> ContentNotifications.lesson(context, data["lessonTitle"].orEmpty())
            "cards" -> ContentNotifications.cards(context, data["lessonId"].orEmpty(), data["lessonTitle"].orEmpty())
            "banner" -> ContentNotifications.banner(context, data["bannerTitle"].orEmpty())
        }
    }
}

/** Textes et envoi des notifications de nouveautés reçues par push. */
internal object ContentNotifications {

    private const val ID_LESSON = 4103
    private const val ID_CARDS = 4104
    private const val ID_BANNER = 4105

    fun lesson(context: Context, title: String) {
        val name = title.ifBlank { "Nouvelle leçon" }
        StudyReminders.post(
            context, ID_LESSON, ContentWatcher.CHANNEL,
            title = "Nouvelle leçon disponible",
            text = "« $name » vient d'être publiée. Viens la découvrir !",
            openDojo = false
        )
    }

    fun cards(context: Context, lessonId: String, title: String) {
        val completed = runBlocking(Dispatchers.IO) {
            KalynoDatabase.get(context).lessonProgressDao().completedLessonIds(StudentId.get(context))
        }
        val inDojo = lessonId in completed
        val name = title.ifBlank { "ta leçon" }
        StudyReminders.post(
            context, ID_CARDS, ContentWatcher.CHANNEL,
            title = "Nouvelles cartes disponibles",
            text = if (inDojo) "Dans le Dojo · $name" else "Dans la leçon « $name »",
            openDojo = inDojo
        )
    }

    fun banner(context: Context, title: String) {
        StudyReminders.post(
            context, ID_BANNER, ContentWatcher.CHANNEL,
            title = "À la une sur KALYNO",
            text = title.ifBlank { "Une nouvelle annonce t'attend" },
            openDojo = false
        )
    }
}
