# Karamay — ProGuard rules

# Keep Hilt-generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# Keep Room entities
-keep class com.karamay.app.data.local.entity.** { *; }

# Keep domain models (used via reflection in Room mapping)
-keep class com.karamay.app.domain.model.** { *; }

# Lottie
-keep class com.airbnb.lottie.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Kotlin serialization / data classes
-keepclassmembers class kotlin.Metadata { *; }
