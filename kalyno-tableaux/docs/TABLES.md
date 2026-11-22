# Tableaux pédagogiques dans KALYNO

## 1. Audit de l'existant (résumé)

| Élément | Constat |
|---|---|
| `LessonBlocks.kt` `toLessonBlock()` | Si `body` contient `<`, le body entier devient **un seul paragraphe** (pas de découpage) → un `<table>` arrive intact à `RichText`. Sinon, découpage sur lignes vides. |
| `RichText.kt` | Si `body` contient `$` et que WebView + assets KaTeX sont disponibles → `KatexText` (WebView). Sinon → `parseRich` (JLaTeXMath, Compose). `parseRich` **ignore** toute balise inconnue (`else -> stack.add(false)`) : `table/tr/td` étaient aplatis en texte collé. |
| `KatexText.kt` + `assets/katex/lesson.html` | `lesson.html` conserve les `<table>` mais sans aucun CSS (pas de bordures), `body{overflow:hidden}` (pas de défilement horizontal). Une WebView par appel. |
| Appels de `RichText` | 3 dans `LessonBlocks.kt` + `MathText.kt`. Le `body` n'est rendu que par `RichText`. |
| `LearningContentCache.kt` | Stocke `body` comme chaîne JSON (`put`/`optString`) : un tableau HTML survit tel quel. **Aucun changement de format.** |
| `SupabaseLearningRepository.kt` | `LearningCapsule` inchangé (id, title, body, minutes, cards, kind, sessionId). |
| Admin `CapsuleEditor.tsx` `cleanHtml()` | Liste blanche de balises **sans table** : un tableau collé/saisi était supprimé à l'enregistrement. À corriger pour que l'admin puisse en publier. |

Meilleur point d'intégration : **`RichText`**, en amont des deux moteurs existants.

## 2. Architecture retenue

```
body HTML ──► RichText
              ├─ contient <table> ? ─► RichTextWithTables
              │      splitTables(html) ─► [Html | Table | Html | Table …]
              │         Html  ─► RichText (moteur actuel, inchangé)
              │         Table ─► parseHtmlTable ─► RichTable (Compose natif)
              │                      cellule ─► RichText(allowKatex = false)  → JLaTeXMath, pas de WebView
              └─ sinon ─► chemin actuel (KaTeX / JLaTeXMath / Text) — strictement inchangé
```

* `ui/explorer/tables/HtmlTable.kt` — Kotlin pur : `splitTables`, `parseHtmlTable`, modèle (`HtmlTable`, `PlacedCell`, `TableCell`), styles CSS.
* `ui/explorer/tables/RichTable.kt` — rendu Compose : `Layout` personnalisé (largeurs de colonnes, hauteurs de lignes, rowspan/colspan), bordures dessinées par cellule, défilement horizontal dans le cadre du tableau.
* Aucune dépendance ajoutée. Aucun second moteur mathématique. Pas de WebView par cellule.

### HTML accepté
`table thead tbody tfoot tr th td caption` ; attributs `rowspan colspan align valign width border` (+ `bgcolor`) ;
styles `border border-width border-color border-collapse(ignoré : toujours fusionné) background-color text-align vertical-align padding width color font-weight`.
Couleurs : `#RGB`, `#RRGGBB`, `rgb()`, quelques noms. Sans `border`/style de bordure : tableau sans lignes (comme en HTML).
`<th>` : gras, centré, fond léger. Tableau plus étroit que l'écran : étiré à la largeur (ou `width`). Plus large : colonnes resserrées (texte à la ligne) jusqu'à 72 dp, puis défilement horizontal.

### Admin (KALYNO-admin)
* `cleanHtml` accepte les balises et attributs ci-dessus (valeurs validées par expressions régulières ; `on*`, `href`, `src`… toujours supprimés).
* Panneau replié « Tableau » : insère un tableau lignes × colonnes avec ou sans en-tête.
* Styles CSS de l'éditeur/aperçu pour afficher les bordures.

## 3. Mode B — exercice interactif (ARCHITECTURE PRÉPARÉE, NON ACTIVÉE)

Principe : **ne pas créer de deuxième système de correction**. Le tableau reste du HTML dans `body` ; l'interactivité est déclarée explicitement :

```html
<table data-kalyno="exercise" border="1">
  <tr><th>Monôme</th><th>Coefficient</th><th>Degré</th></tr>
  <tr><td>$7x^2$</td><td data-answer="7"></td><td data-answer="2"></td></tr>
</table>
```

* Déjà fait : le parseur expose `HtmlTable.interactive` (seulement si `data-kalyno="exercise"`) et `TableCell.answer/editable`. Un tableau ordinaire n'est jamais modifiable (testé). `RichTable` ignore ces champs pour l'instant (rendu document).
* À faire (non réalisé) :
  1. `TableExerciseState` (ViewModel) : `Map<(row,col), String>` des saisies, persisté comme les tentatives du Dojo (`DojoAttempts`).
  2. Cellules `editable` → `BasicTextField` ; Tab/Suivant passe à la cellule suivante ; cellules fixes non focalisables.
  3. Correction : pour chaque cellule, `AnswerGrader.gradeText(typed, AnswerGrader.acceptedAnswers(answer), ctx)` (même moteur que le Dojo, mêmes variantes acceptées), puis note globale → `Verdict`/SRS existants.
  4. Correction expliquée : réutiliser `LearningCard.explanation` d'une carte `FREE_TEXT` rattachée à la capsule (`capsuleId`) — pas de nouvelle table Supabase.
  5. Admin : case « Tableau à compléter » + marquage des cellules-réponses dans l'éditeur.
* Attention : `data-answer` est dans le `body`, donc lisible par un élève qui inspecte les données. Pour un vrai contrôle, stocker les réponses dans la carte (`admin_reference`) et ne garder dans le `body` que les coordonnées. À trancher avant activation.

## 4. Compatibilité
* `LearningCapsule`, Supabase, RLS, cache : inchangés. Anciennes capsules : `splitTables` renvoie le texte d'origine tel quel quand il n'y a pas de `<table` (testé).
* Thème sombre : les couleurs par défaut (texte, bordures, fond d'en-tête) dérivent de la couleur de texte reçue ; les couleurs explicites de l'auteur sont respectées.

## 5. Tests
`app/src/test/java/ci/kalyno/app/ui/tables/HtmlTableTest.kt` (JUnit, JVM) : les 13 cas demandés qui relèvent de l'analyse (tableau simple, plusieurs lignes, cellules vides, fractions, exposants/indices, fusion, couleurs, tableau large, entre deux paragraphes, plusieurs tableaux, HTML sans tableau, anciennes capsules, aller-retour JSON du cache) + robustesse (balises non fermées, ordre thead/tfoot, caption, tableau non fermé) + mode B explicite.

## 6. Limitations
* **Non compilé, non exécuté** dans l'environnement de rédaction (pas de Gradle/Kotlin) : lancer `./gradlew :app:testDebugUnitTest :app:assembleDebug` avant tout usage.
* Rendu Compose (mise en page, bordures, défilement, thème sombre, petit écran) **non vérifié visuellement** ; aucun test instrumenté écrit.
* Tableaux imbriqués non pris en charge.
* Une cellule contenant une formule plus large que sa colonne minimale (72 dp) est rognée au lieu d'élargir la colonne.
* Un tableau placé à l'intérieur d'un `<div style="border…">` (encadré coloré) coupe l'encadré en deux morceaux.
* Les formules des cellules passent par JLaTeXMath (déjà utilisé en repli) ; `\dfrac`/`cases` ont les mêmes variantes de secours que le reste de l'app, mais pas le rendu KaTeX.
* Mode B : architecture et parseur seulement, pas d'interface.
