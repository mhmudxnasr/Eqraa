# ProGuard rules for Eqraa Performance & Safety

# 1. Readium SDK (Reflection & Navigator usage)
-keep class org.readium.** { *; }
-dontwarn org.readium.**

# 2. Kotlin Serialization (JSON parsing)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# 3. Supabase & Network
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# 4. Room Database & Domain Models
-keep class com.eqraa.reader.data.model.** { *; }
-keep class com.eqraa.reader.epub.domain.** { *; }
-keep class com.eqraa.reader.epub.cache.** { *; }

# 5. Timber Logging
-keep class timber.log.** { *; }
-dontwarn timber.log.**

# 6. Compose Compiler Optimizations
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
