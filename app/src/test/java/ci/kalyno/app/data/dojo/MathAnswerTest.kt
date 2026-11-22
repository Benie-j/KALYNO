package ci.kalyno.app.data.dojo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MathAnswerTest {

    private fun cell(typed: String?, vararg accepted: String) = TableGrader.cellResult(typed, accepted.toList())

    private fun ok(expected: String, typed: String) = assertEquals("$expected vs $typed", CellResult.CORRECT, cell(typed, expected))
    private fun ko(expected: String, typed: String) = assertEquals("$expected vs $typed", CellResult.WRONG, cell(typed, expected))

    // ---- tableau de l'énoncé
    @Test fun dfracNegatifContreClavier() = ok("\$\\dfrac{-2}{3}\$", "-2/3")
    @Test fun dfracSansDollars() = ok("\\dfrac{-2}{3}", "-2/3")
    @Test fun fracPositive() = ok("\\frac{2}{3}", "2/3")
    @Test fun dfracNonReduite() = ok("\\dfrac{2}{4}", "1/2")
    @Test fun fracNegativeNonReduite() = ok("\\frac{-4}{6}", "-2/3")
    @Test fun decimalVirguleContreFraction() = ok("0,5", "1/2")
    @Test fun signeOppose1() = ko("-2/3", "2/3")
    @Test fun signeOppose2() = ko("2/3", "-2/3")
    @Test fun fractionsDifferentes() = ko("3/4", "2/3")
    @Test fun signeOpposeAvecLatex() = ko("\$\\dfrac{-2}{3}\$", "2/3")

    // ---- formes et signes
    @Test fun tfracEtEspaces() {
        ok("\\tfrac{1}{2}", " 1 / 2 ")
        ok("1/2", "\\tfrac{1}{2}")
    }

    @Test fun signesPartout() {
        ok("-\\frac{2}{3}", "-2/3")
        ok("\\frac{2}{-3}", "-2/3")
        ok("\\frac{-2}{-3}", "2/3")
        ok("2/-3", "-2/3")
        ok("-2/-3", "2/3")
        ok("\\frac{-2}{3}", "\u22122/3") // vrai signe moins typographique
        ok("-2/3", "- 2 / 3")
    }

    @Test fun reductionSansDouble() {
        ok("1/3", "333333333333333333/999999999999999999") // un Double ne verrait pas la différence ci-dessous
        ko("1/3", "333333333333333333/999999999999999998")
    }

    @Test fun decimaux() {
        ok("1/4", "0,25")
        ok("0.25", "1/4")
        ok("3/2", "1,5")
        ok("2", "4/2")
        ko("1/3", "0,33")
        ko("0,5", "1/3")
        ok("-0,5", "-1/2")
        ok("1,5/3", "1/2")
    }

    @Test fun zeroEgalZero() = ok("0", "-0/5")

    // ---- cas limites
    @Test fun denominateurNulJamaisAccepte() {
        ko("1/2", "1/0")
        ko("1/0", "2/0")
        ko("\\frac{1}{0}", "1/2")
        assertNull(MathAnswer.parse("1/0"))
        assertNull(MathAnswer.parse("\\frac{3}{0}"))
    }

    @Test fun reponseVideEstVide() {
        assertEquals(CellResult.EMPTY, cell("", "1/2"))
        assertEquals(CellResult.EMPTY, cell("   ", "1/2"))
        assertEquals(CellResult.EMPTY, cell(null, "1/2"))
    }

    @Test fun jamaisAccepteEnPartie() {
        ko("2/3", "2/3 cm")
        ko("x = 2/3", "2/3")
        ko("2/3", "x = 2/3")
        ko("2/3", "2/3/4")
        ko("5/3", "1 2/3") // nombre mixte non pris en charge
        ko("2/3", "2")
        ko("12/3", "1 2/3")
        assertNull(MathAnswer.parse("1 2"))
    }

    @Test fun expressionsNonPriseEnChargeRestentTextuelles() {
        assertNull(MathAnswer.parse("\\sqrt{2}"))
        assertNull(MathAnswer.parse("2^3"))
        assertNull(MathAnswer.parse("1/2 + 1/3"))
        ok("\\sqrt{2}", "\\sqrt{2}") // même texte : règle d'origine
        ko("\\sqrt{2}", "1,41")
    }

    // ---- texte ordinaire : règles d'origine inchangées
    @Test fun texteOrdinaire() {
        ok("Évaporation", "  évaporation. ")
        ko("évaporation", "evaporation")
        ko("ou", "où")
        ok("l'eau", "l\u2019eau")
        ko("eau", "l'eau")
        ko("vapeur", "vapeur d'eau")
    }

    @Test fun variantesAdminToujoursAcceptees() {
        assertEquals(CellResult.CORRECT, cell("1/2", "\\dfrac{3}{4}", "0,5"))
        assertEquals(CellResult.CORRECT, cell("demi", "\\dfrac{3}{4}", "demi"))
        assertEquals(CellResult.WRONG, cell("1/3", "\\dfrac{3}{4}", "0,5"))
    }

    @Test fun parseDonneUneValeurExacte() {
        val v = MathAnswer.parse("\$\\dfrac{-4}{6}\$")
        assertNotNull(v)
        assertEquals(MathAnswer.parse("-2/3"), v)
        assertTrue(MathAnswer.equivalent("\\(\\frac{1}{2}\\)", "0,5"))
        assertFalse(MathAnswer.equivalent("1/2", "texte"))
    }

    @Test fun normalizeInchangee() {
        assertEquals("0.5", TableGrader.normalize("0,5"))
        assertEquals("-2/3", TableGrader.normalize("\u22122/3"))
    }
}
