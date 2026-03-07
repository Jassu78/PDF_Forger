# PDF Forger - ProGuard/R8 rules for release build

# Keep pdfbox-android classes (R8 does not support -keepresources)
-keep class com.tom_roush.pdfbox.** { *; }
-keepclassmembers class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# Apache POI: java.awt, javax.xml.stream, Saxon, Batik, OSGi not on Android
-dontwarn java.awt.**
-dontwarn javax.xml.stream.**
-dontwarn net.sf.saxon.**
-dontwarn org.apache.batik.**
-dontwarn org.osgi.**
