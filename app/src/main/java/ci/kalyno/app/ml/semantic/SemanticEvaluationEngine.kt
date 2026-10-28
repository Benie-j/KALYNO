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
 * Moteur d'évaluation sémantique 100 % hors-ligne de KALYNO.
 *
 * Pipeline : tokenisation -> embeddings ONNX (MiniLM int8) -> similarité cosinus -> garde-fous logiques.
 *
 * Utilisation (jamais sur le thread principal) :
 * ```
 * val engine = SemanticEvaluationEngine.getInstance(context)
 * val result = engine.evaluateAsync(reponseAdmin, reponseEleve)
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
        val maxSeqLen: Int = 128,
        val threads: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
    )

    private val inputNames: Set<String> = session.inputNames

    // Les questions sont rejouées souvent : on mémorise les embeddings des références admin.
    private val referenceCache = object : LinkedHashMap<String, FloatArray>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?) = size > CACHE_SIZE
    }

    /** Version bloquante (calcul CPU, quelques dizaines de ms). À appeler hors thread UI. */
    fun evaluate(
        adminReference: String,
        studentAnswer: String,
        thresholds: SemanticThresholds = config.defaultThresholds
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
            val refVec = synchronized(referenceCache) { referenceCache[reference] }
                ?: embed(reference).also { synchronized(referenceCache) { referenceCache[reference] = it } }
            val score = cosine(refVec, embed(answer)).coerceIn(-1f, 1f)
            decide(score, LogicGuard.inspect(reference, answer), thresholds)
        } catch (e: Exception) {
            Log.e(TAG, "Échec de l'évaluation sémantique", e)
            engineError(e.message ?: "erreur inconnue")
        }
    }

    suspend fun evaluateAsync(
        adminReference: String,
        studentAnswer: String,
        thresholds: SemanticThresholds = config.defaultThresholds
    ): EvaluationResult =
        withContext(Dispatchers.Default) { evaluate(adminReference, studentAnswer, thresholds) }

    // ---- Décision --------------------------------------------------------------------------

    private fun decide(score: Float, violation: LogicGuard.Violation?, t: SemanticThresholds): EvaluationResult {
        // Un garde-fou prime TOUJOURS sur le score : une phrase inversée peut avoir 0.95 de similarité.
        if (violation != null) {
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
        return when {
            score >= t.valid -> EvaluationResult(
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

    /** Embedding normalisé L2 (mean pooling sur les tokens, comme à l'entraînement du modèle). */
    private fun embed(text: String): FloatArray {
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
        private const val CACHE_SIZE = 64

        // Incrémenter MODEL_VERSION quand le fichier .onnx des assets change (force la recopie).
        private const val MODEL_VERSION = 1
        private const val MODEL_ASSET = "semantic/model_int8.onnx"
        private const val VOCAB_ASSET = "semantic/vocab.tsv"

        @Volatile private var instance: SemanticEvaluationEngine? = null

        /** Initialisation paresseuse et thread-safe (double-checked locking). Coûteuse au 1er appel. */
        fun getInstance(context: Context, config: Config = Config()): SemanticEvaluationEngine =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext, config).also { instance = it }
            }

        private fun create(context: Context, config: Config): SemanticEvaluationEngine {
            val modelFile = materializeModel(context)
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(config.threads)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val session = env.createSession(modelFile.absolutePath, options)
            val tokenizer = UnigramTokenizer.fromAsset(context, VOCAB_ASSET)
            return SemanticEvaluationEngine(env, session, tokenizer, config)
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
