package ci.kalyno.app.ui.dojo

/**
 * Renforcement en langage familier ivoirien : 15 félicitations + 15 encouragements.
 * Seul texte écrit en dur du Dojo (ce n'est pas du contenu pédagogique) ; aucune question ni réponse ici.
 */
object DojoMessages {

    val congratulations: List<String> = listOf(
        "C'est carré ! Tu as calé ça proprement.",
        "C'est une masterclass !",
        "On est ensemble ! Tu gères ça comme un chef.",
        "Tu as déchiré ça, c'est du lourd !",
        "Le boss, c'est toi, on est d'accord !",
        "Propre, propre ! Même le prof va applaudir.",
        "Tu as tapé dans le mille, mon champion !",
        "Ça, c'est du travail de pro, on est fiers de toi !",
        "Tu es en feu ! Personne ne peut t'arrêter !",
        "Tu as mis ça dans la poche, proprement !",
        "Tu as bien mouillé le maillot, ça paie !",
        "C'est le grand niveau, hein ! Continue comme ça !",
        "Tu as fait ça comme un vrai génie, bravo !",
        "Gagné ! Tu as la main, on te félicite !",
        "Zéro faute, chef ! C'est du solide !"
    )

    val encouragements: List<String> = listOf(
        "Faut pas te décourager mon champion, c'est en ratant qu'on devient un boss.",
        "C'est gâté un peu, mais faut te ressaisir !",
        "Yako ! Regarde la correction et on repart de plus belle.",
        "C'est pas grave, même les meilleurs se trompent. On avance !",
        "On ne lâche pas ! Chaque erreur te rapproche de la bonne réponse.",
        "Ça va aller, tu vas tenir ça la prochaine fois !",
        "Doucement, doucement, tu vas y arriver !",
        "C'est chaud sur celle-là, mais tu es plus fort que ça !",
        "Tu étais proche, retiens bien l'explication et on y retourne !",
        "Pas de stress, on est ensemble. Lis la correction et on continue.",
        "Un petit couac, ce n'est pas la fin du monde. Relève la tête !",
        "Courage ! Même le boss d'hier a raté avant de réussir.",
        "Ça arrive à tout le monde, on corrige et on reprend du service !",
        "Ne baisse pas les bras, la prochaine est pour toi !",
        "Tu as raté celle-là, mais ton cerveau vient de noter la leçon. C'est du progrès !"
    )

    private var lastCongratulation = -1
    private var lastEncouragement = -1

    fun congrats(): String {
        lastCongratulation = pick(congratulations.size, lastCongratulation)
        return congratulations[lastCongratulation]
    }

    fun encourage(): String {
        lastEncouragement = pick(encouragements.size, lastEncouragement)
        return encouragements[lastEncouragement]
    }

    /** Tirage aléatoire sans jamais répéter deux fois de suite le même message. */
    private fun pick(size: Int, last: Int): Int {
        val index = (0 until size).random()
        return if (index == last && size > 1) (index + 1) % size else index
    }
}
