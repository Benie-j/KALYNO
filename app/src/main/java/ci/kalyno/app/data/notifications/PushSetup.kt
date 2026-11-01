package ci.kalyno.app.data.notifications

import android.content.Context
import android.util.Log
import ci.kalyno.app.data.AppPrefs
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Abonne l'appareil aux sujets (topics) Firebase : un par classe, plus un sujet commun « all ».
 * Le serveur envoie ainsi une seule notification à tous les élèves d'une classe, sans stocker de jeton.
 *
 * Sans google-services.json (Firebase non configuré), tout est ignoré : l'appli garde la surveillance
 * toutes les 15 min ([ContentWatcher]) comme solution de secours.
 */
object PushSetup {

    private const val PREFS = "kalyno_push"
    private const val K_TOPIC = "level_topic"
    private const val K_ACTIVE = "active"
    internal const val TOPIC_ALL = "all"
    private const val TAG = "KalynoPush"

    /** Nom du sujet d'une classe ; doit rester identique à celui de la fonction serveur notify-content. */
    fun levelTopic(levelId: String): String = "level_" + levelId.replace(Regex("[^A-Za-z0-9_.~%-]"), "_")

    /** Vrai quand l'abonnement est confirmé : le push remplace alors la surveillance pour les alertes. */
    fun isActive(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_ACTIVE, false)

    /** À appeler au lancement et à chaque contrôle : sans effet si l'abonnement est déjà à jour. */
    fun sync(context: Context) {
        val app = context.applicationContext
        try {
            if (FirebaseApp.getApps(app).isEmpty()) {
                Log.i(TAG, "Firebase non configuré (google-services.json absent) : surveillance 15 min uniquement")
                return
            }
            val level = AppPrefs(app).level ?: return // classe pas encore choisie
            val wanted = levelTopic(level)
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val previous = prefs.getString(K_TOPIC, null)
            if (previous == wanted && prefs.getBoolean(K_ACTIVE, false)) return

            val messaging = FirebaseMessaging.getInstance()
            if (previous != null && previous != wanted) messaging.unsubscribeFromTopic(previous)
            messaging.subscribeToTopic(TOPIC_ALL)
            messaging.subscribeToTopic(wanted)
                .addOnSuccessListener {
                    prefs.edit().putString(K_TOPIC, wanted).putBoolean(K_ACTIVE, true).apply()
                    Log.i(TAG, "Abonné aux notifications push : $wanted")
                }
                .addOnFailureListener { Log.w(TAG, "Abonnement impossible pour l'instant : ${it.message}") }
        } catch (e: Exception) {
            Log.w(TAG, "Push indisponible : ${e.message}")
        }
    }
}
