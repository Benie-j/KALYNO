package ci.kalyno.app.ml.semantic

import android.content.Context
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

/**
 * Tokenizeur Unigram (SentencePiece, algorithme de Viterbi) compatible avec le vocabulaire élagué
 * du modèle paraphrase-multilingual-MiniLM-L12-v2 (voir tools/export_model.py).
 *
 * Format de assets/semantic/vocab.tsv : une ligne par id, "piece<TAB>score".
 * Lignes 0 à 3 réservées : <s>, <pad>, </s>, <unk>.
 */
class UnigramTokenizer private constructor(
    private val pieces: HashMap<String, Int>,
    private val scores: FloatArray,
    private val maxPieceLen: Int,
    private val unkScore: Float
) {

    /** Retourne [BOS] + tokens + [EOS], longueur totale <= maxSeqLen. */
    fun encode(text: String, maxSeqLen: Int = 128): IntArray {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace('\u2019', '\'')
            .lowercase(Locale.ROOT)          // le vocabulaire élagué ne contient que des minuscules
            .replace(WHITESPACE, " ")
            .trim()
            .take(MAX_CHARS)

        val body = if (normalized.isEmpty()) IntArray(0)
        else segment(SPACE_MARK + normalized.replace(' ', SPACE_MARK))

        val kept = min(body.size, maxSeqLen - 2)
        val out = IntArray(kept + 2)
        out[0] = BOS_ID
        System.arraycopy(body, 0, out, 1, kept)
        out[kept + 1] = EOS_ID
        return out
    }

    /** Viterbi : maximise la somme des log-probabilités des pièces. */
    private fun segment(s: String): IntArray {
        val n = s.length
        val best = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val prevStart = IntArray(n + 1)
        val prevId = IntArray(n + 1)
        best[0] = 0f

        for (i in 0 until n) {
            if (best[i] == Float.NEGATIVE_INFINITY) continue
            var hasSingle = false
            for (end in i + 1..min(n, i + maxPieceLen)) {
                val id = pieces[s.substring(i, end)] ?: continue
                if (end == i + 1) hasSingle = true
                val cand = best[i] + scores[id]
                if (cand > best[end]) { best[end] = cand; prevStart[end] = i; prevId[end] = id }
            }
            if (!hasSingle) { // caractère inconnu : repli sur <unk> pour garantir un chemin
                val cand = best[i] + unkScore
                if (cand > best[i + 1]) { best[i + 1] = cand; prevStart[i + 1] = i; prevId[i + 1] = UNK_ID }
            }
        }

        val rev = ArrayList<Int>()
        var pos = n
        while (pos > 0) {
            val id = prevId[pos]
            if (!(id == UNK_ID && rev.lastOrNull() == UNK_ID)) rev.add(id) // fusionne les <unk> consécutifs
            pos = prevStart[pos]
        }
        rev.reverse()
        return rev.toIntArray()
    }

    companion object {
        const val BOS_ID = 0
        const val PAD_ID = 1
        const val EOS_ID = 2
        const val UNK_ID = 3
        private const val SPECIAL_COUNT = 4
        private const val SPACE_MARK = '\u2581'
        private const val MAX_CHARS = 600
        private val WHITESPACE = Regex("\\s+")

        fun fromAsset(context: Context, assetPath: String): UnigramTokenizer {
            val map = HashMap<String, Int>(50_000)
            val scoreList = ArrayList<Float>(50_000)
            context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEachIndexed { idx, line ->
                    val tab = line.lastIndexOf('\t')
                    check(tab > 0) { "vocab.tsv : ligne ${idx + 1} mal formée" }
                    scoreList.add(line.substring(tab + 1).toFloat())
                    if (idx >= SPECIAL_COUNT) map[line.substring(0, tab)] = idx
                }
            }
            check(scoreList.size > SPECIAL_COUNT) { "vocab.tsv vide" }
            val scores = scoreList.toFloatArray()
            val maxLen = map.keys.maxOf { it.length }
            val minScore = (SPECIAL_COUNT until scores.size).minOf { scores[it] }
            return UnigramTokenizer(map, scores, maxLen, minScore - 10f)
        }
    }
}
