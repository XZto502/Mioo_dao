# ── General ──
-keepattributes Signature,Exceptions,*Annotation*,InnerClasses,EnclosingMethod

# ── Moshi (reflection-based) ──
# KSP Moshi codegen auto-generates rules for @JsonClass models.
# This keep rule is for any data classes that might be deserialized via KotlinJsonAdapterFactory reflection.
-keepclassmembers class com.mioo.dao.data.model.** {
    <init>(...);
    <fields>;
}

# ── Hilt / Dagger ──
-keep class dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-dontwarn dagger.hilt.internal.**

# ── Retrofit ──
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ── OkHttp / Okio ──
-dontwarn okhttp3.internal.platform.**
-dontwarn okio.**

# ── Coil ──
-dontwarn coil.annotation.**

# ── Coroutines ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── ZXing ──
-dontwarn com.journeyapps.barcodescanner.**

# ── Kotlin metadata (needed by reflection libs) ──
-keep class kotlin.Metadata { *; }

# ── Compose (minimal — Compose doesn't use reflection) ──
-dontwarn androidx.compose.**
