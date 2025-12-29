# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}

# Keep Room entities and DAOs
-keep class takagi.ru.monica.wear.data.** { *; }

# Keep Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep WebDAV client classes
-keep class com.thegrizzlylabs.sardineandroid.** { *; }

# Keep Compose classes
-keep class androidx.compose.** { *; }
-keep class androidx.wear.compose.** { *; }

# Fix for XmlResourceParser issue
-dontwarn org.xmlpull.v1.**
-dontwarn org.xmlpull.**
-keep class org.xmlpull.** { *; }
-keep interface org.xmlpull.** { *; }

# Suppress warnings about missing classes
-dontwarn android.content.res.XmlResourceParser
-ignorewarnings
