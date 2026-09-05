# Proguard rules for diffview library module

# Gson serialization rules
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# Keep comment data models serialized with Gson
-keep class com.mdiwebma.diffview.comment.** { *; }
-keepclassmembers class com.mdiwebma.diffview.comment.** { *; }
