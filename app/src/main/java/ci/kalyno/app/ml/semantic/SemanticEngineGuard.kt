package ci.kalyno.app.ml.semantic

import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.Process

/**
 * Garde-fou contre les plantages NATIFS d'ONNX Runtime.
 *
 * Un SIGBUS/SIGSEGV dans libonnxruntime.so ne peut pas être attrapé en Kotlin : le processus meurt.
 * On écrit donc un marqueur « tentative en cours » (synchrone, avant l'appel natif) qu'on efface à la
 * réussite. Au lancement suivant, un marqueur resté en place prouve que la tentative précédente a tué
 * l'appli : on passe au palier inférieur au lieu de replanter en boucle.
 *
 *   palier 0 : optimisations complètes   ->  palier 1 : sans optimisation  ->  palier 2 : moteur désactivé
 *
 * Désactivé, le Dojo corrige la saisie libre par mots-clés (repli déjà prévu dans DojoViewModel).
 * Les paliers sont remis à zéro à chaque nouvelle version de l'appli (nouvel ONNX Runtime = nouvel essai).
 */
internal class SemanticEngineGuard(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Palier à utiliser pour la prochaine création de session, ou null si le moteur est désactivé.
     * Marque la tentative comme « en cours » : à clore par [markSuccess] ou [markJavaFailure].
     */
    @Synchronized
    fun beginAttempt(): OrtSession.SessionOptions.OptLevel? {
        val tier = resolveTier()
        if (tier >= TIER_DISABLED) return null
        prefs.edit().putBoolean(KEY_PENDING, true).commit() // commit() : doit être sur disque AVANT le code natif
        attemptInProgress = true
        return if (tier == TIER_FULL) {
            OrtSession.SessionOptions.OptLevel.ALL_OPT
        } else {
            OrtSession.SessionOptions.OptLevel.NO_OPT
        }
    }

    @Synchronized
    fun markSuccess() = clearPending()

    /** Exception Java (pas un plantage natif) : on n'abaisse pas le palier, une panne passagère n'est pas un bug ONNX. */
    @Synchronized
    fun markJavaFailure() = clearPending()

    /** Vrai si une tentative de création de session peut encore avoir lieu. */
    @Synchronized
    fun isAvailable(): Boolean = resolveTier() < TIER_DISABLED

    private fun clearPending() {
        attemptInProgress = false
        prefs.edit().putBoolean(KEY_PENDING, false).commit()
    }

    private fun resolveTier(): Int {
        val stored = prefs.getInt(KEY_TIER, initialTier())
        var tier = stored
        val edit = prefs.edit()
        var changed = false

        if (prefs.getInt(KEY_VERSION, -1) != versionCode()) {
            tier = initialTier()
            edit.putInt(KEY_VERSION, versionCode()).putBoolean(KEY_PENDING, false)
            changed = true
        } else if (prefs.getBoolean(KEY_PENDING, false) && !attemptInProgress) {
            // Marqueur laissé par un processus précédent : la création de session l'a tué
            tier = (tier + 1).coerceAtMost(TIER_DISABLED)
            edit.putBoolean(KEY_PENDING, false)
            changed = true
        }
        if (changed || tier != stored) edit.putInt(KEY_TIER, tier).commit() // aucune écriture disque en régime normal
        return tier
    }

    /**
     * Les journaux montrent le même SIGBUS (accès non aligné, MurmurHash2) dans ONNX Runtime sur ce
     * téléphone 32 bits, y compris pour une autre appli : on n'y lance pas le moteur par défaut.
     */
    private fun initialTier(): Int =
        if (Process.is64Bit() || ENABLE_ON_32_BIT) TIER_FULL else TIER_DISABLED

    @Suppress("DEPRECATION")
    private fun versionCode(): Int = try {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionCode
    } catch (e: Exception) {
        0
    }

    private companion object {
        const val PREFS = "kalyno_semantic_guard"
        const val KEY_TIER = "tier"
        const val KEY_PENDING = "pending"
        const val KEY_VERSION = "version"
        const val TIER_FULL = 0
        const val TIER_DISABLED = 2

        /** Passer à true pour retenter ONNX sur les appareils 32 bits (le palier « sans optimisation » puis la coupure s'appliquent). */
        const val ENABLE_ON_32_BIT = false

        /** Vrai tant que CE processus a une création de session en cours (distingue un marqueur périmé). */
        @Volatile var attemptInProgress = false
    }
}
