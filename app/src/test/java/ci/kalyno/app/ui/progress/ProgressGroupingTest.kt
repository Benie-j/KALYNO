package ci.kalyno.app.ui.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressGroupingTest {
    private data class Row(val id: Int, val subject: String?, val lesson: String?)

    private fun group(vararg rows: Row) = groupBySubjectAndLesson(rows.toList(), { it.subject }, { it.lesson })

    @Test fun classeParMatierePuisParLecon() {
        val groups = group(
            Row(1, "Maths", "Fractions"),
            Row(2, "SVT", "Cellule"),
            Row(3, "Maths", "Fractions"),
            Row(4, "Maths", "Équations")
        )
        assertEquals(listOf("Maths", "SVT"), groups.map { it.title })
        assertEquals(listOf("Fractions", "Équations"), groups[0].lessons.map { it.title })
        assertEquals(listOf(1, 3), groups[0].lessons[0].items.map { it.id })
        assertEquals(3, groups[0].count)
        assertEquals(1, groups[1].count)
    }

    @Test fun conserveLOrdreDArrivee() {
        val groups = group(Row(1, "SVT", "A"), Row(2, "Maths", "B"), Row(3, "SVT", "C"))
        assertEquals(listOf("SVT", "Maths"), groups.map { it.title })
        assertEquals(listOf("A", "C"), groups[0].lessons.map { it.title })
    }

    @Test fun matiereOuLeconVideNeDisparaitPas() {
        val groups = group(Row(1, null, "  "), Row(2, "", "Cours"))
        assertEquals(listOf(NO_SUBJECT), groups.map { it.title })
        assertEquals(setOf(NO_LESSON, "Cours"), groups[0].lessons.map { it.title }.toSet())
        assertEquals(2, groups[0].count)
    }

    @Test fun listeVideDonneAucunGroupe() = assertTrue(group().isEmpty())

    @Test fun espacesAutourDuNomSontIgnores() {
        val groups = group(Row(1, " Maths ", "L"), Row(2, "Maths", "L"))
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].count)
    }
}
