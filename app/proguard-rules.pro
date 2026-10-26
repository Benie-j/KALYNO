# Bibliotheques qui chargent des classes ou ressources dynamiquement
-keep class ru.noties.jlatexmath.** { *; }
-keep class com.caverock.androidsvg.** { *; }
-dontwarn ru.noties.jlatexmath.**
-dontwarn com.caverock.androidsvg.**

# Garde les numeros de ligne pour lire les rapports de plantage
-keepattributes SourceFile,LineNumberTable
