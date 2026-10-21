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
# R8 full mode erases Continuation<T> to a raw Continuation. Retrofit casts that
# parameter to ParameterizedType while parsing a suspend method, which crashes
# the first call whose signature was stripped (thread detail).
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ── OkHttp / Okio ──
-dontwarn okhttp3.internal.platform.**
-dontwarn okio.**

# ── Coil ──
-dontwarn coil.annotation.**
-dontwarn coil.decode.**
# 图片缓存上限会通过 RealDiskCache 的 cache 字段调用 DiskLruCache.close 来裁剪。
# 混淆后字段名必须还叫 cache，否则关不掉旧缓存，会同时打开两份目录。
-keepclassmembers class coil.disk.RealDiskCache {
    coil.disk.DiskLruCache cache;
}
-keepclassmembers class coil.disk.DiskLruCache {
    public void close();
}

# ── Release JSON models and Retrofit interfaces ──
# Moshi codegen covers @JsonClass types. These keeps also protect reflection
# adapters and Retrofit suspend methods if a model is missed by codegen.
-keep class com.mioo.dao.data.model.** { *; }
-keep interface com.mioo.dao.data.api.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# ── Coroutines ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ── ZXing ──
-dontwarn com.journeyapps.barcodescanner.**

# ── Kotlin metadata (needed by reflection libs) ──
-keep class kotlin.Metadata { *; }

# ── Compose (minimal — Compose doesn't use reflection) ──
-dontwarn androidx.compose.**
