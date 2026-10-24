package ci.kalyno.app.data.srs

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Paramètres FSRS-6 (21 poids). Les valeurs par défaut conviennent à tout le monde ;
 * une version future pourra en fournir de personnalisés sans toucher au moteur.
 *
 * [desiredRetention] : probabilité visée de se souvenir le jour de la révision (0,9 = 90 %).
 */
class FsrsParams(
    val w: DoubleArray = DEFAULT_WEIGHTS,
    val desiredRetention: Double = 0.9
) {
    init {
        require(w.size == 21) { "FSRS-6 attend 21 paramètres" }
        require(desiredRetention in 0.7..0.99) { "rétention visée hors limites" }
    }

    /** Exposant de la courbe de l'oubli. */
    val decay: Double = -w[20]

    /** Facteur qui garantit R(S, S) = 90 %. */
    val factor: Double = 0.9.pow(1.0 / decay) - 1.0

    companion object {
        val DEFAULT_WEIGHTS: DoubleArray = doubleArrayOf(
            0.2172, 1.1771, 3.2602, 16.1507, 7.0114, 0.5700, 2.0966, 0.0069,
            1.5261, 0.1120, 1.0178, 1.8490, 0.1133, 0.3127, 2.2934, 0.2191,
            3.0004, 0.7536, 0.3332, 0.1437, 0.2000
        )

        val DEFAULT = FsrsParams()
    }
}

/**
 * Moteur de calcul FSRS (Free Spaced Repetition Scheduler), séparé de Room et d'Android
 * pour rester testable sur la JVM.
 *
 * Chaque carte porte deux valeurs de mémoire :
 * - la stabilité S : nombre de jours au bout desquels la probabilité de se souvenir tombe à 90 % ;
 * - la difficulté D : de 1 (facile) à 10 (difficile).
 *
 * Une carte créée avant FSRS (stabilité à 0) est reprise automatiquement : sa stabilité vaut son
 * ancien intervalle, sa difficulté se déduit de son ancien facteur de facilité. Aucune progression perdue.
 *
 * Garde-fous :
 * - échéance calée sur le début de journée (la carte apparaît le matin du jour prévu) ;
 * - intervalles ordonnés : Oublié = 1 jour, puis Hésité < Su < Trop facile ;
 * - [maxIntervalDays] plafonne l'intervalle (date d'examen publiée par l'admin) ;
 * - léger étalement déterministe des longs intervalles pour éviter les pics de révision.
 */
object SrsEngine {

    const val MAX_INTERVAL_DAYS = 365

    /** Part du temps restant avant l'examen qu'un intervalle peut occuper : au moins ~2 passages avant le jour J. */
    const val EXAM_REVIEW_SPLIT = 0.5

    /** Poids du coefficient dans l'urgence : racine carrée, pour que les gros coefficients n'écrasent pas les autres matières. */
    const val COEFFICIENT_EXPONENT = 0.5

    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val MIN_STABILITY = 0.01
    private const val MAX_STABILITY = 36_500.0

    // ----------------------------------------------------------------------------------------------
    // Dates (java.time)
    // ----------------------------------------------------------------------------------------------

    fun dateOf(timeMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(timeMillis).atZone(zone).toLocalDate()

    /** Début de journée (00:00) dans le fuseau donné. */
    fun startOfDay(timeMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        dateOf(timeMillis, zone).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Nombre de jours calendaires entre aujourd'hui et [date] (négatif si la date est passée). */
    fun daysUntil(now: Long, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int =
        ChronoUnit.DAYS.between(dateOf(now, zone), date).toInt()

    private fun dueDateMillis(now: Long, days: Int, zone: ZoneId): Long =
        dateOf(now, zone).plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()

    /**
     * Plus grand intervalle autorisé aujourd'hui pour qu'une carte revienne avant l'examen.
     *
     * - pas de date d'examen, ou examen passé : aucun plafond ;
     * - sinon l'intervalle est au plus la moitié du temps restant (au moins 2 jours tant que
     *   l'examen est à plus de 2 jours, jamais au-delà de la veille de l'examen).
     */
    fun examIntervalCap(now: Long, examDate: LocalDate?, zone: ZoneId = ZoneId.systemDefault()): Int {
        if (examDate == null) return MAX_INTERVAL_DAYS
        val daysLeft = daysUntil(now, examDate, zone)
        if (daysLeft < 0) return MAX_INTERVAL_DAYS
        val half = ((daysLeft - 1) * EXAM_REVIEW_SPLIT).toInt()
        return max(1, min(daysLeft - 1, max(2, half))).coerceAtMost(MAX_INTERVAL_DAYS)
    }

    // ----------------------------------------------------------------------------------------------
    // Modèle de mémoire
    // ----------------------------------------------------------------------------------------------

    /** Probabilité de se souvenir après [elapsedDays] jours pour une stabilité [stability]. */
    fun retrievability(
        elapsedDays: Double,
        stability: Double,
        params: FsrsParams = FsrsParams.DEFAULT
    ): Double {
        if (stability <= 0.0) return 0.0
        return (1.0 + params.factor * max(0.0, elapsedDays) / stability).pow(params.decay)
    }

    /** Intervalle (en jours, non arrondi) qui ramène la rétention à la valeur visée. */
    fun intervalFor(stability: Double, params: FsrsParams = FsrsParams.DEFAULT): Double =
        stability / params.factor * (params.desiredRetention.pow(1.0 / params.decay) - 1.0)

    /** Ancien facteur de facilité (1,3 à 3,0) -> difficulté FSRS (10 à ~3) ; 2,5 -> 5. */
    private fun difficultyFromEase(ease: Double): Double =
        (9.0 - (ease - 1.3) * (4.0 / 1.2)).coerceIn(1.0, 10.0)

    /** Difficulté FSRS -> facteur de facilité conservé pour le cloud et les anciennes versions. */
    private fun easeFromDifficulty(difficulty: Double): Double =
        (1.3 + (9.0 - difficulty) * 0.3).coerceIn(1.3, 3.0)

    private class Memory(val stability: Double, val difficulty: Double)

    /** Mémoire de la carte, reprise de l'ancien état si besoin ; null pour une carte jamais révisée. */
    private fun memoryOf(card: CardProgressEntity): Memory? = when {
        card.stability > 0.0 -> Memory(card.stability, card.difficulty.coerceIn(1.0, 10.0))
        card.intervalDays > 0 -> Memory(card.intervalDays.toDouble(), difficultyFromEase(card.easeFactor.toDouble()))
        else -> null
    }

    private fun elapsedDays(card: CardProgressEntity, now: Long, zone: ZoneId): Int {
        val last = card.lastReviewedAt ?: return card.intervalDays.coerceAtLeast(0)
        return max(0, ChronoUnit.DAYS.between(dateOf(last, zone), dateOf(now, zone)).toInt())
    }

    private fun initialStability(grade: Int, p: FsrsParams): Double =
        p.w[grade - 1].coerceIn(MIN_STABILITY, MAX_STABILITY)

    private fun initialDifficulty(grade: Int, p: FsrsParams): Double =
        (p.w[4] - exp(p.w[5] * (grade - 1)) + 1.0).coerceIn(1.0, 10.0)

    private fun nextDifficulty(d: Double, grade: Int, p: FsrsParams): Double {
        val w = p.w
        val damped = d + (-w[6] * (grade - 3)) * (10.0 - d) / 9.0
        val easyStart = w[4] - exp(w[5] * 3.0) + 1.0
        return (w[7] * easyStart + (1.0 - w[7]) * damped).coerceIn(1.0, 10.0)
    }

    private fun nextStability(m: Memory, grade: Int, elapsed: Int, p: FsrsParams): Double {
        val w = p.w
        val s = m.stability
        val next = if (elapsed <= 0) {
            // Révision le jour même : la mémoire bouge peu
            val sameDay = s * exp(w[17] * (grade - 3 + w[18])) * s.pow(-w[19])
            if (grade >= 3) max(sameDay, s) else sameDay
        } else {
            val r = retrievability(elapsed.toDouble(), s, p)
            if (grade == 1) {
                val afterLapse = w[11] * m.difficulty.pow(-w[12]) *
                    ((s + 1.0).pow(w[13]) - 1.0) * exp(w[14] * (1.0 - r))
                min(afterLapse, s / exp(w[17] * w[18]))
            } else {
                val hardPenalty = if (grade == 2) w[15] else 1.0
                val easyBonus = if (grade == 4) w[16] else 1.0
                s * (1.0 + exp(w[8]) * (11.0 - m.difficulty) * s.pow(-w[9]) *
                    (exp(w[10] * (1.0 - r)) - 1.0) * hardPenalty * easyBonus)
            }
        }
        return next.coerceIn(MIN_STABILITY, MAX_STABILITY)
    }

    /** Étalement déterministe : même carte, même décalage ; cartes différentes, jours différents. */
    private fun withSpread(interval: Int, cardId: String, repetitions: Int): Int {
        val spread = when {
            interval >= 60 -> 3
            interval >= 30 -> 2
            interval >= 7 -> 1
            else -> 0
        }
        if (spread == 0) return interval
        val seed = cardId.hashCode() * 31 + repetitions
        return interval + Math.floorMod(seed, 2 * spread + 1) - spread
    }

    // ----------------------------------------------------------------------------------------------
    // Planification
    // ----------------------------------------------------------------------------------------------

    /** Résultat des quatre notes possibles (indices 0 à 3 = notes 1 à 4). */
    private class Plan(
        val stability: DoubleArray,
        val difficulty: DoubleArray,
        val intervals: IntArray,
        val elapsed: Int,
        val isNew: Boolean
    )

    private fun plan(
        current: CardProgressEntity,
        now: Long,
        zone: ZoneId,
        cap: Int,
        params: FsrsParams
    ): Plan {
        val memory = memoryOf(current)
        val elapsed = if (memory == null) 0 else elapsedDays(current, now, zone)
        val stability = DoubleArray(4)
        val difficulty = DoubleArray(4)
        for (grade in 1..4) {
            val i = grade - 1
            if (memory == null) {
                stability[i] = initialStability(grade, params)
                difficulty[i] = initialDifficulty(grade, params)
            } else {
                difficulty[i] = nextDifficulty(memory.difficulty, grade, params)
                stability[i] = nextStability(memory, grade, elapsed, params)
            }
        }

        fun raw(grade: Int): Int = withSpread(
            max(1, intervalFor(stability[grade - 1], params).roundToInt()),
            current.flashcardId,
            current.repetitions
        )

        // Oublié : retour demain. Ensuite Hésité < Su < Trop facile, toujours strictement croissants.
        val hard = min(raw(2), raw(3))
        val good = max(raw(3), hard + 1)
        val easy = max(raw(4), good + 1)
        val intervals = intArrayOf(1, hard, good, easy)
        for (i in intervals.indices) intervals[i] = intervals[i].coerceIn(1, cap)

        return Plan(stability, difficulty, intervals, elapsed, memory == null)
    }

    /**
     * Calcule la nouvelle progression d'une carte après une révision.
     *
     * @param maxIntervalDays plafond de l'intervalle (voir [examIntervalCap])
     */
    fun computeNextProgress(
        current: CardProgressEntity,
        rating: ReviewRating,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        maxIntervalDays: Int = MAX_INTERVAL_DAYS,
        params: FsrsParams = FsrsParams.DEFAULT
    ): CardProgressEntity {
        val cap = maxIntervalDays.coerceIn(1, MAX_INTERVAL_DAYS)
        val plan = plan(current, now, zone, cap, params)
        val i = rating.grade - 1
        val interval = plan.intervals[i]
        val lapsed = rating == ReviewRating.OUBLIE && !plan.isNew && plan.elapsed >= 1

        return current.copy(
            easeFactor = easeFromDifficulty(plan.difficulty[i]).toFloat(),
            intervalDays = interval,
            repetitions = if (rating == ReviewRating.OUBLIE) 0 else current.repetitions + 1,
            dueDate = dueDateMillis(now, interval, zone),
            lastReviewedAt = now,
            stability = plan.stability[i],
            difficulty = plan.difficulty[i],
            lapses = current.lapses + if (lapsed) 1 else 0
        )
    }

    /** Intervalle (en jours) que donnerait chacune des quatre notes : Oublié, Hésité, Su, Trop facile. */
    fun previewIntervals(
        current: CardProgressEntity,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        maxIntervalDays: Int = MAX_INTERVAL_DAYS,
        params: FsrsParams = FsrsParams.DEFAULT
    ): IntArray {
        val cap = maxIntervalDays.coerceIn(1, MAX_INTERVAL_DAYS)
        return plan(current, now, zone, cap, params).intervals.copyOf()
    }

    // ----------------------------------------------------------------------------------------------
    // Priorité du jour
    // ----------------------------------------------------------------------------------------------

    /**
     * Score d'urgence d'une carte.
     *
     * Score = coefficient^0,5 × (1 − probabilité de s'en souvenir maintenant)
     *
     * - La probabilité vient de la courbe de l'oubli : une carte fragile en retard pèse bien plus
     *   qu'une carte solide au même retard.
     * - Le coefficient de la matière à l'examen reste le multiplicateur d'importance, adouci par
     *   la racine carrée pour ne pas étouffer les petites matières.
     */
    fun urgencyScore(
        card: CardWithContext,
        now: Long = System.currentTimeMillis(),
        params: FsrsParams = FsrsParams.DEFAULT
    ): Float {
        val stability = if (card.stability > 0.0) card.stability else max(1, card.intervalDays).toDouble()
        val last = card.lastReviewedAt ?: (card.dueDate - card.intervalDays.coerceAtLeast(0) * DAY_MS)
        val elapsed = max(0.0, (now - last) / DAY_MS.toDouble())
        val forgetting = 1.0 - retrievability(elapsed, stability, params)
        val weight = card.coefficient.toDouble().coerceAtLeast(0.0).pow(COEFFICIENT_EXPONENT)
        return (weight * forgetting).toFloat()
    }

    /**
     * Trie les cartes dues par score d'urgence décroissant (à égalité : la plus ancienne échéance d'abord).
     * Les cartes pas encore dues sont exclues.
     */
    fun prioritize(cards: List<CardWithContext>, now: Long = System.currentTimeMillis()): List<CardWithContext> {
        return cards
            .filter { it.dueDate <= now }
            .map { it to urgencyScore(it, now) }
            .sortedWith(
                compareByDescending<Pair<CardWithContext, Float>> { it.second }
                    .thenBy { it.first.dueDate }
            )
            .map { it.first }
    }
}
