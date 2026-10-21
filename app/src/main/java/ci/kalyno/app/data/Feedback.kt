package ci.kalyno.app.data

object Feedback {
    private val success = listOf(
        "On est ensemble ! Bonne réponse ! 🔥",
        "Tu gères ça, c'est ça même !",
        "Tu as tapé dans le mille, on est ensemble !",
        "Tu es en feu ! Continue comme ça !",
        "Le boss, c'est toi ! 👏",
        "Tu as déchiré ça, c'est du lourd !",
        "Chapeau ! Tu as bien travaillé !",
        "Ça, c'est du travail bien fait !"
    )

    private val retry = listOf(
        "Dommage ! Tu étais si proche de la bonne réponse.",
        "Yako ! Regarde la bonne réponse et on repart.",
        "Pas de problème, c'est comme ça qu'on apprend. Courage !",
        "Ça va aller, on ne lâche pas !",
        "Doucement, doucement, tu vas y arriver !",
        "C'est pas grave, même les meilleurs se trompent. On avance !",
        "Ne baisse pas les bras, la prochaine est pour toi !",
        "Yako ! Tu y es presque, retiens celle-là."
    )

    fun success(): String = success.random()
    fun retry(): String = retry.random()
}
