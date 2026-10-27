package ci.kalyno.app.data.badges

enum class BadgeCategory { STREAK, FSRS, EXPLORATION, SOCIAL }

/** Définition statique d'un badge (configuration UX, pas une donnée pédagogique admin-driven). */
data class BadgeDef(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val category: BadgeCategory,
    val targetValue: Int
)

/** Nomenclature officielle, nouchi / esprit étudiant — ne pas modifier sans validation produit. */
object BadgeCatalog {
    val all: List<BadgeDef> = listOf(
        BadgeDef("streak_1", "Premier Mouvement", "Le premier pas est posé, tu es officiellement entré dans le mouvement.", "🔹", BadgeCategory.STREAK, 1),
        BadgeDef("streak_7", "Le boss", "Une semaine complète de constance. La machine est en marche, ça ne blague plus !", "🔥", BadgeCategory.STREAK, 7),
        BadgeDef("streak_30", "L'intouchable", "Un mois entier sans sauter un seul jour. Le sommet t'appartient, personne ne peut t'arrêter.", "👑", BadgeCategory.STREAK, 30),

        BadgeDef("fsrs_50cards", "Le Cerveau en Béton", "50 cartes révisées. La mémoire se construit, brique par brique.", "🧠", BadgeCategory.FSRS, 50),
        BadgeDef("fsrs_zero_pioche", "Zéro Pioche", "100% de réussite du premier coup au Dojo. Du lourd.", "🎯", BadgeCategory.FSRS, 100),
        BadgeDef("fsrs_retention_85", "Esprit de Cadre", "Rétention globale supérieure à 85%. Ça reste gravé.", "⚡", BadgeCategory.FSRS, 85),

        BadgeDef("explo_5_chapters", "Rat de Bibliothèque 2.0", "5 chapitres explorés. Tu creuses, tu apprends.", "📚", BadgeCategory.EXPLORATION, 5),
        BadgeDef("explo_discipline_100", "Major du Canton", "100% des défis d'une discipline validés. Respect.", "🏆", BadgeCategory.EXPLORATION, 100),

        BadgeDef("social_first_share", "C'est Carré", "Premier partage de citation ou de score réussi. Propre.", "✅", BadgeCategory.SOCIAL, 1)
    )

    fun byId(id: String): BadgeDef? = all.firstOrNull { it.id == id }
}
