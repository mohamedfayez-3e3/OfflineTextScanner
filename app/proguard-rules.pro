-keep class com.googlecode.tesseract.android.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**
