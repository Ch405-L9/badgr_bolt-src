# ── Strip verbose logging from release builds ─────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ── Retrofit ──────────────────────────────────────────────────────────────────
# Retrofit 2.11 ships its own consumer ProGuard rules; a blanket -keep is redundant
# and blocks R8 optimization. Only the generic-signature/exception attributes are
# still needed for its parameterized Call<T> return types.
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes Exceptions

# ── Gson ──────────────────────────────────────────────────────────────────────
# Gson needs annotations + generic signatures and the app model classes (kept below),
# not the Gson library classes themselves. Custom (de)serializers are still kept.
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── OkHttp ────────────────────────────────────────────────────────────────────
# OkHttp/Okio ship consumer rules; -dontwarn is sufficient, blanket -keep removed.
-dontwarn okhttp3.**
-dontwarn okio.**

# ── App model classes (prevent Gson stripping) ────────────────────────────────
-keep class com.badgr.orbreader.data.model.** { *; }
-keep class com.badgr.orbreader.data.local.** { *; }
-keep class com.badgr.orbreader.data.remote.** { *; }

# ── DataStore ─────────────────────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }

# ── Coil ─────────────────────────────────────────────────────────────────────
-dontwarn coil.**

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firebase Auth
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.firebase.auth.** { *; }

# Firestore
-keep class com.google.firebase.firestore.** { *; }

# Crashlytics
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**

# Google Play Billing (for 2.3.x milestone)
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# Play Integrity (ships consumer rules; keep + dontwarn for safety)
-keep class com.google.android.play.core.integrity.** { *; }
-dontwarn com.google.android.play.core.integrity.**

# CloudSyncManager and ProGate — never obfuscate entitlement logic
-keep class com.badgr.orbreader.sync.** { *; }
-keep class com.badgr.orbreader.billing.** { *; }
