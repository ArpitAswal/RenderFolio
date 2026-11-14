# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod

# Model & Serialization Keep Rules (Moshi & Kotlin Serialization)
-keep class com.example.model.** { *; }
-keep class com.example.constant.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.JsonQualifier <fields>;
}
-keep class * extends com.squareup.moshi.JsonAdapter
-keep class com.squareup.moshi.** { *; }
-dontnote kotlinx.serialization.**

# Apache PDFBox Android & FontBox
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.apache.fontbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn org.apache.fontbox.**

# BouncyCastle Cryptography
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.xml.stream.**
-dontwarn javax.imageio.**
-dontwarn java.awt.**

# Google ML Kit Text Recognition
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ZXing QR Code Generation
-keep class com.google.zxing.** { *; }

# AndroidX Room
-keep class * extends androidx.room.RoomDatabase
-keep class * implements androidx.room.migration.Migration
-dontwarn androidx.room.paging.**

# ViewBinding & Material Design
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(...);
    public static * bind(...);
}

