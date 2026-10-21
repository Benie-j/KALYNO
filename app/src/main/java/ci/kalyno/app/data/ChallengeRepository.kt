package ci.kalyno.app.data

import kotlinx.coroutines.delay

interface ChallengeRepository {
    suspend fun firstChallenge(levelId: String): Challenge
}

/** TEMPORAIRE : questions d'essai pour tester l'écran. Sera remplacé par l'espace admin. */
class SampleChallengeRepository : ChallengeRepository {
    override suspend fun firstChallenge(levelId: String): Challenge {
        delay(300)
        return Challenge("Premier défi · ${Levels.label(levelId)}", SAMPLE)
    }

    private companion object {
        private fun q(id: String, prompt: String, a: String, b: String, c: String, d: String, correct: Int) =
            Question(id, prompt, listOf(a, b, c, d), correct)

        val SAMPLE = listOf(
            q("1", "Quelle est la capitale politique de la Côte d'Ivoire ?", "Abidjan", "Yamoussoukro", "Bouaké", "San-Pédro", 1),
            q("2", "Combien font 7 × 8 ?", "54", "56", "58", "64", 1),
            q("3", "Quel est le plus long fleuve de Côte d'Ivoire ?", "Comoé", "Sassandra", "Bandama", "Cavally", 2),
            q("4", "Quelle est la formule chimique de l'eau ?", "H2O", "CO2", "O2", "NaCl", 0),
            q("5", "En quelle année la Côte d'Ivoire est-elle devenue indépendante ?", "1958", "1960", "1962", "1964", 1),
            q("6", "Quelle est la racine carrée de 144 ?", "10", "11", "12", "14", 2),
            q("7", "Quel est le contraire de « généreux » ?", "Avare", "Aimable", "Riche", "Large", 0),
            q("8", "Quelle planète est la plus proche du Soleil ?", "Vénus", "Mercure", "Mars", "Terre", 1),
            q("9", "Qui a été le premier président de la Côte d'Ivoire ?", "Félix Houphouët-Boigny", "Laurent Gbagbo", "Henri Konan Bédié", "Alassane Ouattara", 0),
            q("10", "Si 2x + 6 = 14, combien vaut x ?", "3", "4", "5", "8", 1)
        )
    }
}
