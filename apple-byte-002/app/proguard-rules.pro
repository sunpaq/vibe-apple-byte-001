# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK directory.

# Keep ARCore classes
-keep class com.google.ar.** { *; }
-keep class com.google.ar.core.** { *; }

# Keep OpenCV classes
-keep class org.opencv.** { *; }

# Keep CameraX classes
-keep class androidx.camera.** { *; }
