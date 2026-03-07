# PDF Forger - ProGuard/R8 rules for release build

# Keep pdfbox-android classes (R8 does not support -keepresources)
-keep class com.tom_roush.pdfbox.** { *; }
-keepclassmembers class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
