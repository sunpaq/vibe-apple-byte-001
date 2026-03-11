# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep ARCore classes
-keep class com.google.ar.** { *; }

# Keep OpenCV classes
-keep class org.opencv.** { *; }

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
