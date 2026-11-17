# Jackson models inside youtubedl-android are populated reflectively.
-keep class com.yausername.youtubedl_android.mapper.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.io.**
