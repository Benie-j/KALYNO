package ci.kalyno.app.data.srs

import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Mesure à quel point des paramètres FSRS prédisent les vraies révisions des élèves.
 *
 * Principe : on rejoue l'historique de chaque carte avec le moteur. Avant chaque révision
 * (hors première et hors même jour), on calcule la probabilité de réussite prédite, puis on la
 * compare au résultat réel (réussi = Hésité, Su ou Trop facile ; échec = Oublié).
 *
 * Plus la perte (logLoss) et l'écart de calibration (rmseBins) sont bas, mieux les paramètres
 * collent aux élèves. La référence est un modèle naïf qui prédit toujours la moyenne.
 */
object FsrsEvaluator {

    private const val BINS = 10
    private const val EPS = 1e-4

    class Report(
        val reviews: Int,
        val logLoss: Double,
        val baselineLogLoss: Double,
        val rmseBins: Double,
        val predictedMean: Double,
        val actualMean: Double
    ) {
        val beatsBaseline: Boolean get() = reviews > 0 && logLoss < baselineLogLoss

        fun summary(): String = String.format(
            Locale.US,
            "revisions=%d perte=%.4f (reference %.4f) calibration=%.4f predit=%.1f%% reel=%.1f%%",
            reviews, logLoss, baselineLogLoss, rmseBins, predictedMean * 100, actualMean * 100
        )
    }

    fun evaluate(
        logs: List<ReviewLogEntity>,
        params: FsrsParams = FsrsParams.DEFAULT,
        zone: ZoneId = ZoneId.systemDefault()
    ): Report {
        var n = 0
        var lossSum = 0.0
        var predSum = 0.0
        var actualSum = 0.0
        val binCount = IntArray(BINS)
        val binPred = DoubleArray(BINS)
        val binActual = DoubleArray(BINS)

        val byCard = logs.groupBy { it.studentId to it.flashcardId }
        for (history in byCard.values) {
            var card = CardProgressEntity(
                studentId = history[0].studentId,
                flashcardId = history[0].flashcardId,
                dueDate = 0L
            )
            for (log in history.sortedBy { it.reviewedAt }) {
                val last = card.lastReviewedAt
                if (last != null && card.stability > 0.0) {
                    val elapsed = ChronoUnit.DAYS.between(
                        SrsEngine.dateOf(last, zone),
                        SrsEngine.dateOf(log.reviewedAt, zone)
                    )
                    if (elapsed >= 1) {
                        val p = SrsEngine.retrievability(elapsed.toDouble(), card.stability, params)
                            .coerceIn(EPS, 1.0 - EPS)
                        val y = if (log.rating.grade >= 2) 1.0 else 0.0
                        lossSum += -(y * ln(p) + (1.0 - y) * ln(1.0 - p))
                        n++
                        predSum += p
                        actualSum += y
                        val bin = (p * BINS).toInt().coerceAtMost(BINS - 1)
                        binCount[bin]++
                        binPred[bin] += p
                        binActual[bin] += y
                    }
                }
                card = SrsEngine.computeNextProgress(
                    card, log.rating, now = log.reviewedAt, zone = zone, params = params
                )
            }
        }

        if (n == 0) return Report(0, 0.0, 0.0, 0.0, 0.0, 0.0)

        val actualMean = actualSum / n
        val pbar = actualMean.coerceIn(EPS, 1.0 - EPS)
        val baseline = -(pbar * ln(pbar) + (1.0 - pbar) * ln(1.0 - pbar))

        var sq = 0.0
        for (b in 0 until BINS) {
            if (binCount[b] == 0) continue
            val diff = binPred[b] / binCount[b] - binActual[b] / binCount[b]
            sq += binCount[b] * diff * diff
        }

        return Report(
            reviews = n,
            logLoss = lossSum / n,
            baselineLogLoss = baseline,
            rmseBins = sqrt(sq / n),
            predictedMean = predSum / n,
            actualMean = actualMean
        )
    }
}
