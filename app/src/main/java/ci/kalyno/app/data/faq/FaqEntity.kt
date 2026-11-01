package ci.kalyno.app.data.faq

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Une question de la FAQ, copie locale d'une ligne de `faq_items` (Supabase).
 *
 * Room est ce que l'élève lit : la FAQ reste donc complète hors ligne. L'admin est la seule source :
 * la table locale est un miroir, remplacé à chaque synchronisation réussie (voir [FaqDao.replaceAll]).
 *
 * `id` est l'UUID Supabase (texte). `updatedAt` est la date de dernière modification en epoch ms.
 */
@Entity(tableName = "faq_items")
data class FaqEntity(
    @PrimaryKey val id: String,
    val category: String,
    val question: String,
    val answer: String,
    val orderIndex: Int,
    val updatedAt: Long
)
