# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep OpenCV classes
-keep class org.opencv.** { *; }

# Keep ArUco related classes
-keep class org.opencv.aruco.** { *; }

# Keep Calib3d related classes
-keep class org.opencv.calib3d.** { *; }

# Keep Features2D related classes
-keep class org.opencv.features2d.** { *; }
