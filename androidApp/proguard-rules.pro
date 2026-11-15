# Keep the launcher entry point and Compose runtime bits that R8 cannot see.
-keep class com.kyant.backdrop.catalog.MainActivity { *; }
-keep class com.kyant.backdrop.catalog.launcher.** { *; }

# Compose
-dontwarn androidx.compose.**
-keepclassmembers class androidx.compose.runtime.** { *; }

# Kotlin metadata used by Compose compiler output
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Compose Multiplatform resources
-keep class glass.app.generated.resources.** { *; }
