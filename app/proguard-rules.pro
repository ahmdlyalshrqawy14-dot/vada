# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Data Models
-keep class com.example.data.db.** { *; }
-keep class com.example.data.model.** { *; }

# Moshi rules
-keep class com.squareup.moshi.** { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <fields>;
}

# Keep Coroutines
-keepclassmembers class * {
    @kotlinx.coroutines.** *;
}

# ============================================================================
# PdfBox-Android (com.tom-roush:pdfbox-android) - CRITICAL for release builds
# ============================================================================
# This is *the* real bug behind "it works when I test it but breaks once it's
# actually built as a real app": release builds here have isMinifyEnabled=true
# and isShrinkResources=true (see app/build.gradle.kts), but pdfbox-android
# and its font/compat sub-libraries load a lot of their own classes via
# reflection at runtime (font handling, image codecs, JDK-compatibility
# shims). Without explicit -keep rules, R8 has no way to know those classes
# are still needed, so it strips or renames them. The result: the debug build
# (unminified) works perfectly, but the exact same code in a signed release
# build throws ClassNotFoundException / NoSuchMethodError deep inside PDFBox
# the moment you try to compress/merge/split a PDF or convert Office docs to
# PDF (which also uses PDFBox's JPEGFactory to embed images) - every PDF-
# related feature in the entire app was one release build away from breaking
# silently, with no reproduction in local debug testing.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**
-dontwarn com.tom_roush.harmony.**
-dontwarn org.apache.commons.logging.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.print.**
-dontwarn org.osgi.**

# ============================================================================
# WebView print pipeline (OfficeToPdfConverter / WebViewPdfPrinter)
# ============================================================================
# Our own WebViewClient / PrintDocumentAdapter callback subclasses are plain
# overrides (not reached via reflection), so R8 handles them correctly on its
# own - but keep the classes visible by name anyway since the system Print
# framework holds a live reference to the adapter instance across process
# boundaries (Binder) during onLayout/onWrite, and a defensive -keep here
# costs nothing and rules out an entire class of "works standalone, breaks
# once R8 renames/merges the callback class" failures.
-keep class com.example.data.util.WebViewPdfPrinter { *; }
-keep class com.example.data.util.WebViewPdfPrinter$* { *; }

