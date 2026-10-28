# Bibliotheques qui chargent des classes ou ressources dynamiquement
-keep class ru.noties.jlatexmath.** { *; }
-keep class com.caverock.androidsvg.** { *; }
-dontwarn ru.noties.jlatexmath.**
-dontwarn com.caverock.androidsvg.**

# Garde les numeros de ligne pour lire les rapports de plantage
-keepattributes SourceFile,LineNumberTable

# Pont JavaScript du moteur KaTeX (hauteur des formules)
-keepclassmembers class ci.kalyno.app.ui.explorer.HeightBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# KALYNO - ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
