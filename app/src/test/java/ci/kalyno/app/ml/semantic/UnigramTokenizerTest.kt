package ci.kalyno.app.ml.semantic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Tokenizer testé sur le VRAI vocabulaire embarqué (JVM, sans Android). */
class UnigramTokenizerTest {

    private val vocabFile = File("src/main/assets/semantic/e5_vocab.tsv")
    private val tok by lazy { UnigramTokenizer.fromLines(vocabFile.readLines(Charsets.UTF_8).asSequence()) }

    @Test fun lesIdsSpeciauxSontLesQuatrePremiersDuModele() {
        val ids = tok.encode("x")
        assertEquals(UnigramTokenizer.BOS_ID, ids.first())
        assertEquals(UnigramTokenizer.EOS_ID, ids.last())
    }

    @Test fun uneSequenceVideDonneBosEtEos() {
        assertEquals(listOf(0, 2), tok.encode("   ").toList())
    }

    @Test fun leFrancaisCourantNeProduitAucunInconnu() {
        val text = "query: Le noyau contient l'information génétique et contrôle les activités de la cellule. " +
            "La Côte d'Ivoire est indépendante depuis 1960 ; l'hypoténuse² = a² + b²."
        val ids = tok.encode(text)
        assertTrue("des <unk> dans : ${ids.toList()}", UnigramTokenizer.UNK_ID !in ids)
    }

    @Test fun lesMajusculesEtLesApostrophesTypographiquesDonnentLesMemesIds() {
        assertEquals(tok.encode("query: Côte d'Ivoire").toList(), tok.encode("query: côte d’ivoire").toList())
    }

    @Test fun lePrefixeE5EstStable() {
        // Les premiers tokens de « query: … » ne dépendent pas de la suite du texte
        val a = tok.encode("query: la cellule").toList().take(4)
        val b = tok.encode("query: le cœur").toList().take(4)
        assertEquals(a, b)
    }

    @Test fun laTroncatureGardeToujoursEos() {
        val ids = tok.encode("cellule ".repeat(400), maxSeqLen = 32)
        assertEquals(32, ids.size)
        assertEquals(UnigramTokenizer.EOS_ID, ids.last())
    }

    @Test fun tousLesIdsSontDansLeVocabulaire() {
        val ids = tok.encode("query: La décolonisation et l'indépendance des pays africains en 1960")
        assertTrue(ids.all { it in 0 until tok.vocabSize })
    }

    @Test fun unVocabulaireDecaleEstRefuse() {
        val bad = sequenceOf("<pad>\t0.0", "<s>\t0.0", "</s>\t0.0", "<unk>\t0.0", "a\t-1.0")
        val r = runCatching { UnigramTokenizer.fromLines(bad) }
        assertTrue(r.isFailure)
    }

    /**
     * Conformité avec le tokenizer de référence : tools/export_e5.py écrit tokenizer_golden.json à partir du tokenizer
     * HuggingFace (sentencepiece) du modèle. Ignoré tant que ce fichier n'a pas été généré (lancer l'export une fois).
     */
    @Test fun memesIdsQueLeTokenizerDeReference() {
        val golden = File("src/test/resources/semantic/tokenizer_golden.tsv")
        assumeTrue("tokenizer_golden.tsv absent : lancer tools/export_e5.py", golden.exists())
        for (line in golden.readLines(Charsets.UTF_8).filter { it.isNotBlank() }) {
            val (text, expected) = line.split('\t')
            assertEquals("texte : $text", expected, tok.encode(text, maxSeqLen = 512).joinToString(","))
        }
    }
}
