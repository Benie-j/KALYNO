package ci.kalyno.app.data

sealed interface ChallengeResult {
    data class Loaded(val challenge: Challenge) : ChallengeResult
    data object Empty : ChallengeResult
    data class Failed(val message: String) : ChallengeResult
}

interface ChallengeRepository {
    /** Retourne uniquement le défi publié pour ce niveau. */
    suspend fun firstChallenge(levelId: String): ChallengeResult
}

/** En attendant le back-end : aucun contenu. L'admin publiera les défis par niveau. */
class NoContentRepository : ChallengeRepository {
    override suspend fun firstChallenge(levelId: String): ChallengeResult = ChallengeResult.Empty
}
