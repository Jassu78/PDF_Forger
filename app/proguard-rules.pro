# PDF Forger - ProGuard rules for release build

# Keep pdfbox-android classes and resources (glyphlist, AFM fonts)
-keep class com.tom_roush.pdfbox.** { *; }
-keepclassmembers class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-keepresources **/glyphlist*
