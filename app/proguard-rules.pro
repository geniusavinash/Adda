# Keep kotlinx-serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class co.mobilise.adda.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor / Netty reflective bits
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn io.netty.**
-dontwarn org.slf4j.**

# MediaPipe GenAI
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
