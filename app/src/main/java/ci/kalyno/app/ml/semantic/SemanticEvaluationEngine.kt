package ci.kalyno.app.ml.semantic

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * Moteur d'évaluation sémantique 100 % hors-ligne de KALYNO, basé sur intfloat/multilingual-e5-small.
 *
 * Pipeline : préfixes E5 ("query: " / "passage: ") -> tokenisation -> embeddings ONNX (int8)
 *            -> moyenne des tokens + normalisation L2 -> similarité cosinus -> garde-fous logiques.
 *
 * Utilisation (jamais sur le thread principal) :
 * ```
 * val engine = SemanticEvaluationEngine.getInstance(context)
 * val result = engine.evaluateAsync(reponseAdmin, reponseEleve, thresholds, question = enonce)
 * ```
 */
class SemanticEvaluationEngine private constructor(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    private val tokenizer: UnigramTokenizer,
    private val config: Config
) : AutoCloseable {

    /** Réglages du moteur. Le seuil est volontairement strict (rigueur pédagogique). */
    data class Config(
        val defaultThresholds: SemanticThresholds = SemanticThresholds.DEFAULT,
        val maxSeqLen: Int = 256,   // E5 accepte 512 tokens ; le passage enrichi par la leçon est plus long qu'une réponse sèche
        val threads: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
    )

    private val inputNames: Set<String> = session.inputNames

    // Les questions sont rejouées souvent : on mémorise les embeddings des références admin.
    private val referenceCache = object : LinkedHashMap<String, FloatArray>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > CACHE_SIZE
    }

    /**
     * Version bloquante (calcul CPU, quelques dizaines de ms). À appeler hors thread UI.
     *
     * Réponse de l'élève et réponse officielle sont deux textes de même nature : tâche SYMÉTRIQUE de la fiche E5,
     * donc « query: » des deux côtés (« passage: » est réservé à la recherche d'un texte de cours).
     * [question] sert uniquement à repérer les termes propres à la réponse ([ContentGuard]).
     */
    fun evaluate(
        adminReference: String,
        studentAnswer: String,
        thresholds: SemanticThresholds = config.defaultThresholds,
        question: String = ""
    ): EvaluationResult {
        val reference = adminReference.trim()
        val answer = studentAnswer.trim()

        if (answer.isEmpty()) {
            return EvaluationResult(
                false, 0f,
                "Ta réponse est vide. Écris ce que tu as retenu de la leçon, même en quelques mots.",
                EvaluationVerdict.EMPTY_INPUT
            )
        }
        if (reference.isEmpty()) return engineError("Référence admin vide")

        return try {
            val score = cosine(referenceVector(E5Prompt.query(reference)), embed(E5Prompt.query(answer))).coerceIn(-1f, 1f)
            decide(
                score, LogicGuard.inspect(reference, answer), thresholds,
                coverage = ContentGuard.coverage(reference, answer, question)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Échec de l'évaluation sémantique", e)
            engineError(e.message ?: "erreur inconnue")
        }
    }

    /** Embedding d'un texte de référence, mémorisé (références admin et passages de leçon). */
    private fun referenceVector(text: String): FloatArray =
        synchronized(referenceCache) { referenceCache[text] }
            ?: embed(text).also { synchronized(referenceCache) { referenceCache[text] = it } }

    /**
     * Rattrapage par la leçon, quand les mots-clés ont refusé la réponse. Recherche de texte au sens de la fiche E5
     * (« query: » contre « passage: »).
     *
     * 1) le passage de leçon le plus proche de « question + bonne réponse courte » est retenu, à condition de
     *    s'imposer nettement sur le deuxième (l'ORDRE relatif compte, pas la valeur absolue : en E5 un passage hors
     *    sujet dépasse déjà 0,75) ;
     * 2) la réponse de l'élève est comparée à CE passage ;
     * 3) les garde-fous (négation, antonymes, chiffres) s'appliquent à la bonne réponse courte, jamais au passage ;
     * 4) un passage de cours ressemble à toute réponse sur le même thème, vraie ou fausse : le résultat ne peut donc
     *    JAMAIS être « validé ». Au mieux « presque », avec renvoi vers la leçon.
     *
     * Retourne ENGINE_ERROR si aucun passage n'est pertinent : l'appelant garde alors sa correction par mots-clés.
     */
    fun evaluateWithLesson(
        question: String,
        expectedShort: String,
        passages: List<String>,
        studentAnswer: String
    ): EvaluationResult {
        val answer = studentAnswer.trim()
        if (answer.isEmpty()) {
            return EvaluationResult(
                false, 0f,
                "Ta réponse est vide. Écris ce que tu as retenu de la leçon, même en quelques mots.",
                EvaluationVerdict.EMPTY_INPUT
            )
        }
        if (passages.isEmpty()) return engineError("Aucun passage de leçon")

        return try {
            val queryVec = embed(E5Prompt.query("${question.trim()} ${expectedShort.trim()}"))
            val ranked = passages
                .map { referenceVector(E5Prompt.passage(it)).let { vec -> cosine(queryVec, vec) to vec } }
                .sortedByDescending { it.first }
            val (top, topVec) = ranked.first()
            val margin = if (ranked.size > 1) top - ranked[1].first else Float.MAX_VALUE
            if (top < LESSON_RETRIEVAL_MIN || margin < LESSON_RETRIEVAL_MARGIN) {
                return engineError("Aucun passage de leçon nettement pertinent (score=$top marge=$margin)")
            }

            val score = cosine(topVec, embed(E5Prompt.query(answer))).coerceIn(-1f, 1f)
            decideFromLesson(score, LogicGuard.inspect(expectedShort, answer))
        } catch (e: Exception) {
            Log.e(TAG, "Échec de l'évaluation par la leçon", e)
            engineError(e.message ?: "erreur inconnue")
        }
    }

    suspend fun evaluateWithLessonAsync(
        question: String,
        expectedShort: String,
        passages: List<String>,
        studentAnswer: String,
    ): EvaluationResult =
        withContext(Dispatchers.Default) { evaluateWithLesson(question, expectedShort, passages, studentAnswer) }

    /** Calcule à l'avance les vecteurs des passages de leçon (en arrière-plan) pour que la première correction soit instantanée. */
    suspend fun preloadAsync(texts: List<String>) = withContext(Dispatchers.Default) {
        for (text in texts) {
            try { referenceVector(E5Prompt.passage(text)) } catch (e: Exception) { Log.w(TAG, "Préchargement ignoré : ${e.message}") }
        }
    }

    /** Précalcule les vecteurs des réponses officielles des cartes de la session (premier calcul hors saisie). */
    suspend fun preloadReferencesAsync(references: List<String>) = withContext(Dispatchers.Default) {
        for (text in references) {
            try { referenceVector(E5Prompt.query(text)) } catch (e: Exception) { Log.w(TAG, "Préchargement ignoré : ${e.message}") }
        }
    }

    suspend fun evaluateAsync(
        adminReference: String,
        studentAnswer: String,
        thresholds: SemanticThresholds = config.defaultThresholds,
        question: String = ""
    ): EvaluationResult =
        withContext(Dispatchers.Default) {
            evaluate(adminReference, studentAnswer, thresholds, question)
        }

    // ---- Décision --------------------------------------------------------------------------

    /** Message pédagogique d'un garde-fou déclenché. */
    private fun contradiction(score: Float, violation: LogicGuard.Violation): EvaluationResult {
        val msg = when (violation) {
            LogicGuard.Violation.NEGATION_MISMATCH ->
                "Attention au sens de ta phrase : la tienne et la réponse attendue n'ont pas la même polarité " +
                "(affirmation ou négation). Relis la leçon et vérifie ce qui est affirmé et ce qui est nié."
            LogicGuard.Violation.ANTONYM_CONFLICT ->
                "Un terme clé de ta réponse exprime l'inverse de la notion attendue. " +
                "Vérifie le sens exact du vocabulaire de la leçon."
            LogicGuard.Violation.NUMBER_MISMATCH ->
                "Les valeurs numériques de ta réponse ne correspondent pas à celles attendues. " +
                "Vérifie tes chiffres et leurs unités."
        }
        return EvaluationResult(false, score, msg, EvaluationVerdict.CONTRADICTION)
    }

    /** Rattrapage par la leçon : jamais « validé » (voir [evaluateWithLesson]). */
    private fun decideFromLesson(score: Float, violation: LogicGuard.Violation?): EvaluationResult {
        Log.d(TAG, "leçon score=$score partiel>=$LESSON_PARTIAL garde-fou=$violation")
        if (violation != null) return contradiction(score, violation)
        return if (score >= LESSON_PARTIAL) EvaluationResult(
            false, score,
            "Ta réponse est proche du cours, mais elle n'est pas assez précise pour être validée. " +
            "Relis le passage de la leçon et reprends ses termes exacts.",
            EvaluationVerdict.PARTIAL
        ) else EvaluationResult(
            false, score,
            "Ta réponse s'éloigne de la notion attendue. Relis la leçon, puis reformule avec tes propres mots.",
            EvaluationVerdict.INCORRECT
        )
    }

    /**
     * Décision à partir de la réponse officielle. « Validé » exige TROIS conditions : score >= seuil, aucun
     * garde-fou logique, et les termes propres de la réponse officielle repris ([coverage]).
     */
    private fun decide(score: Float, violation: LogicGuard.Violation?, t: SemanticThresholds, coverage: Float): EvaluationResult {
        Log.d(TAG, "score=$score valid=${t.valid} partial=${t.partial} couverture=$coverage garde-fou=$violation") // aide au calibrage
        // Un garde-fou prime TOUJOURS sur le score : une phrase inversée peut avoir 0.95 de similarité.
        if (violation != null) return contradiction(score, violation)
        return when {
            score >= t.valid && coverage >= ContentGuard.MIN_COVERAGE -> EvaluationResult(
                true, score,
                "Réponse validée. Ta formulation correspond à la notion attendue.",
                EvaluationVerdict.CORRECT
            )
            score >= t.partial -> EvaluationResult(
                false, score,
                "Tu es sur la bonne piste, mais ta réponse est incomplète ou imprécise. " +
                "Reprends les termes clés de la leçon et complète ta formulation.",
                EvaluationVerdict.PARTIAL
            )
            else -> EvaluationResult(
                false, score,
                "Ta réponse s'éloigne de la notion attendue. Relis la leçon, puis reformule avec tes propres mots.",
                EvaluationVerdict.INCORRECT
            )
        }
    }

    private fun engineError(detail: String): EvaluationResult {
        Log.w(TAG, "Moteur indisponible : $detail")
        return EvaluationResult(
            false, 0f,
            "L'évaluation automatique est momentanément indisponible. Ta réponse n'a pas été notée.",
            EvaluationVerdict.ENGINE_ERROR
        )
    }

    // ---- Inférence -------------------------------------------------------------------------

    /**
     * Embedding normalisé L2 : moyenne des tokens pondérée par attention_mask puis normalisation,
     * exactement le `average_pool` + `F.normalize` de la fiche du modèle E5.
     * Une seule séquence sans padding : le masque vaut 1 partout, la moyenne simple est donc identique.
     * Le texte reçu doit déjà porter son préfixe « query: » ou « passage: » ([E5Prompt]).
     */
    private fun embed(text: String): FloatArray {
        // Fiche E5 : chaque texte DOIT porter « query: » ou « passage: ». Un oubli (ou un double appel à
        // E5Prompt) dégrade silencieusement TOUS les scores : on préfère échouer bruyamment.
        require(E5Prompt.hasPrefix(text)) { "Préfixe E5 manquant : \"${text.take(30)}\"" }
        require(!text.startsWith(E5Prompt.QUERY + E5Prompt.QUERY) && !text.startsWith(E5Prompt.PASSAGE + E5Prompt.PASSAGE)) {
            "Préfixe E5 en double : \"${text.take(30)}\""
        }
        val ids = tokenizer.encode(text, config.maxSeqLen)
        val n = ids.size
        val shape = longArrayOf(1L, n.toLong())
        val tensors = ArrayList<OnnxTensor>(3)
        try {
            val inputs = HashMap<String, OnnxTensor>()
            fun add(name: String, data: LongArray) {
                if (name in inputNames) {
                    OnnxTensor.createTensor(env, LongBuffer.wrap(data), shape).also { tensors.add(it); inputs[name] = it }
                }
            }
            add("input_ids", LongArray(n) { ids[it].toLong() })
            add("attention_mask", LongArray(n) { 1L })
            add("token_type_ids", LongArray(n))

            session.run(inputs).use { result ->
                val out = result[0] as OnnxTensor
                val dims = out.info.shape
                val buf = out.floatBuffer
                val dim = dims.last().toInt()
                val vec = FloatArray(dim)
                if (dims.size == 3) {                      // [1, seq, dim] -> mean pooling
                    val seq = dims[1].toInt()
                    for (t in 0 until seq) for (d in 0 until dim) vec[d] += buf.get(t * dim + d)
                    for (d in 0 until dim) vec[d] /= seq
                } else {                                    // [1, dim] : déjà poolé
                    buf.get(vec)
                }
                return l2Normalize(vec)
            }
        } finally {
            tensors.forEach { it.close() }
        }
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum).takeIf { it > 1e-12f } ?: return v
        for (i in v.indices) v[i] /= norm
        return v
    }

    /** Similarité cosinus : (a·b) / (||a|| ||b||). */
    internal fun cosine(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Dimensions différentes : ${a.size} vs ${b.size}" }
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom < 1e-12f) 0f else dot / denom
    }

    override fun close() {
        synchronized(Companion) { instance = null }
        session.close()
    }

    // ---- Singleton -------------------------------------------------------------------------

    companion object {
        private const val TAG = "SemanticEngine"
        // Références admin + passages de leçon d'une session (≈ 1,5 Ko par vecteur de 384 dimensions)
        private const val CACHE_SIZE = 160

        // Mesures sur le modèle embarqué : passage pertinent >= 0,86 avec 0,05 d'avance en moyenne sur le suivant ;
        // question hors programme <= 0,79 avec moins de 0,01 d'avance. L'ancien plancher 0,78 laissait tout passer.
        internal const val LESSON_RETRIEVAL_MIN = 0.84f
        internal const val LESSON_RETRIEVAL_MARGIN = 0.02f

        /** Réponse -> passage de cours : en dessous, la réponse est jugée hors sujet (provisoire, à calibrer). */
        internal const val LESSON_PARTIAL = 0.86f

        // Incrémenter MODEL_VERSION quand le fichier .onnx des assets change (force la recopie).
        // v2 = multilingual-e5-small (v1 = ancien MiniLM, supprimé du stockage interne à la 1re ouverture).
        private const val MODEL_VERSION = 2
        private const val MODEL_ASSET = "semantic/e5_model_int8.onnx"
        private const val VOCAB_ASSET = "semantic/e5_vocab.tsv"

        @Volatile private var instance: SemanticEvaluationEngine? = null

        /** Initialisation paresseuse et thread-safe (double-checked locking). Coûteuse au 1er appel. */
        fun getInstance(context: Context, config: Config = Config()): SemanticEvaluationEngine =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext, config).also { instance = it }
            }

        /** Faux quand le garde-fou a coupé le moteur (plantage natif antérieur ou appareil 32 bits) : ne rien charger. */
        fun isUsable(context: Context): Boolean =
            instance != null || SemanticEngineGuard(context.applicationContext).isAvailable()

        private fun create(context: Context, config: Config): SemanticEvaluationEngine {
            val guard = SemanticEngineGuard(context)
            val level = guard.beginAttempt()
                ?: throw IllegalStateException("Moteur sémantique désactivé sur cet appareil")
            try {
                val modelFile = materializeModel(context)
                val env = OrtEnvironment.getEnvironment()
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(config.threads)
                    setOptimizationLevel(level)
                }
                val session = env.createSession(modelFile.absolutePath, options) // un plantage natif a lieu ici
                val tokenizer = UnigramTokenizer.fromAsset(context, VOCAB_ASSET)
                return SemanticEvaluationEngine(env, session, tokenizer, config).also { guard.markSuccess() }
            } catch (t: Throwable) {
                guard.markJavaFailure()
                throw t
            }
        }

        /**
         * Copie le modèle depuis assets vers le stockage interne : ONNX Runtime le charge par chemin
         * (mmap), ce qui évite de monter 35 Mo dans le tas Java sur les téléphones d'entrée de gamme.
         */
        private fun materializeModel(context: Context): File {
            val dir = File(context.noBackupFilesDir, "semantic").apply { mkdirs() }
            val target = File(dir, "model_v$MODEL_VERSION.onnx")
            if (target.exists() && target.length() > 0) return target

            val tmp = File(dir, target.name + ".tmp")
            context.assets.open(MODEL_ASSET).use { input ->
                tmp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
            check(tmp.renameTo(target)) { "Impossible de finaliser la copie du modèle" }
            dir.listFiles()?.filter { it.name.startsWith("model_") && it != target }?.forEach { it.delete() }
            return target
        }
    }
}
