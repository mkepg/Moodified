# Moodified — ProGuard rules

# ---------- Hilt / Dagger ----------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep,allowobfuscation,allowshrinking class * extends androidx.lifecycle.ViewModel
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# ---------- Hilt Work / WorkManager ----------
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep @dagger.assisted.AssistedInject class * { *; }
-keep @dagger.assisted.AssistedFactory interface * { *; }

# ---------- Room ----------
-keep class com.moodified.app.data.local.entity.** { *; }
-keep class com.moodified.app.data.local.database.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.TypeConverter class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# ---------- Domain models (Room/serialization reflection) ----------
-keep class com.moodified.app.domain.model.** { *; }

# ---------- Lottie ----------
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ---------- Kotlin / Coroutines ----------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlin.Metadata { *; }
-keepclassmembernames class kotlinx.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ---------- Compose ----------
-keep,allowobfuscation,allowshrinking class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.** { *; }
# Preserve Compose lambdas to keep stack traces useful
-keepattributes SourceFile,LineNumberTable
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# ---------- Google Play Services (ActivityRecognition) ----------
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# ---------- App entrypoints (Receivers, Service, Application) ----------
-keep class com.moodified.app.MoodifiedApplication { *; }
-keep class com.moodified.app.core.service.** { *; }
-keep class com.moodified.app.data.receiver.** { *; }

# ---------- Strip verbose logging in release ----------
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# Preserve mapping for crash deobfuscation
-printmapping mapping.txt
