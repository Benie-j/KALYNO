package ci.kalyno.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakLogicTest {

    private val today = 20_000L

    @Test fun premiereSessionDonneUn() {
        assertEquals(StreakState(1, today), StreakLogic.recordCompletion(StreakState.EMPTY, today))
    }

    @Test fun sessionDuLendemainAugmente() {
        assertEquals(StreakState(4, today), StreakLogic.recordCompletion(StreakState(3, today - 1), today))
    }

    @Test fun memeJourNeChangeRien() {
        assertEquals(StreakState(3, today), StreakLogic.recordCompletion(StreakState(3, today), today))
    }

    @Test fun interruptionDePlusDUnJourRepartAUn() {
        assertEquals(StreakState(1, today), StreakLogic.recordCompletion(StreakState(9, today - 2), today))
    }

    @Test fun serieAfficheeEstZeroSiInterrompue() {
        assertEquals(0, StreakLogic.current(StreakState(5, today - 2), today))
        assertEquals(5, StreakLogic.current(StreakState(5, today - 1), today))
    }

    @Test fun localVideNeRemplacePasLeDistant() {
        val remote = StreakState(6, today)
        val m = StreakLogic.merge(StreakState.EMPTY, remote)
        assertEquals(remote, m.state)
        assertTrue(m.restoreLocal)
        assertFalse(m.pushRemote)
    }

    @Test fun localZeroAvecJourNeRemplacePasLeDistant() {
        val m = StreakLogic.merge(StreakState(0, today), StreakState(6, today - 1))
        assertEquals(StreakState(6, today - 1), m.state)
        assertFalse(m.pushRemote)
    }

    @Test fun aucuneLigneDistanteEnvoieLeLocal() {
        val local = StreakState(2, today)
        val m = StreakLogic.merge(local, null)
        assertEquals(local, m.state)
        assertTrue(m.pushRemote)
        assertFalse(m.restoreLocal)
    }

    @Test fun rienAFusionnerNeFaitRien() {
        val m = StreakLogic.merge(StreakState.EMPTY, null)
        assertFalse(m.pushRemote)
        assertFalse(m.restoreLocal)
    }

    @Test fun jourLocalPlusRecentEstEnvoye() {
        val m = StreakLogic.merge(StreakState(5, today), StreakState(9, today - 6))
        // L'ancienne chaîne distante est trop loin : on ne l'additionne pas
        assertEquals(StreakState(5, today), m.state)
        assertTrue(m.pushRemote)
    }

    @Test fun jourDistantPlusRecentEstRestaure() {
        val m = StreakLogic.merge(StreakState(9, today - 5), StreakState(2, today))
        assertEquals(StreakState(2, today), m.state)
        assertTrue(m.restoreLocal)
        assertFalse(m.pushRemote)
    }

    @Test fun memeJourGardeLaSerieLaPlusLongue() {
        val m = StreakLogic.merge(StreakState(3, today), StreakState(5, today))
        assertEquals(StreakState(5, today), m.state)
        assertTrue(m.restoreLocal)
        assertFalse(m.pushRemote)
    }

    @Test fun memeJourEtMemeSerieNeSynchroniseRien() {
        val s = StreakState(4, today)
        val m = StreakLogic.merge(s, s)
        assertFalse(m.restoreLocal)
        assertFalse(m.pushRemote)
    }

    @Test fun chainesContiguesSontPrologeesSansAdditionner() {
        // Appareil A : seulement le jour 10 (serie 1). Appareil B : jours 6 a 9 (serie 4). Vraie chaine : 6 a 10 = 5.
        val m = StreakLogic.merge(StreakState(1, today), StreakState(4, today - 1))
        assertEquals(StreakState(5, today), m.state)
        assertTrue(m.pushRemote)
    }

    @Test fun chainesQuiSeChevauchentNeSontPasDoublees() {
        // A : jours 8 a 10 (3). B : jours 6 a 9 (4). Union : 6 a 10 = 5 (et non 3 + 4 = 7)
        val m = StreakLogic.merge(StreakState(3, today), StreakState(4, today - 1))
        assertEquals(StreakState(5, today), m.state)
    }

    @Test fun trouDeUnJourRompLaChaine() {
        // B s'arrete au jour 8, A recommence au jour 10 : le jour 9 manque
        val m = StreakLogic.merge(StreakState(1, today), StreakState(4, today - 2))
        assertEquals(StreakState(1, today), m.state)
    }
}
