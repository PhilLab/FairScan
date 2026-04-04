# Keep OpenCV JNI classes so that native method registration works.
# Only the modules included in the minimal build are strictly needed,
# but keeping the full org.opencv.** is harmless and avoids subtle issues
# if a class is referenced indirectly.
-keep class org.opencv.** { *; }

# The minimal OpenCV build does not include Android resource compilation, so
# org.opencv.R$styleable is never generated. Suppress the R8 missing-class
# error that arises from CameraBridgeViewBase referencing it. But none of the camera
# view classes are currently used in the project
-dontwarn org.opencv.R$styleable
