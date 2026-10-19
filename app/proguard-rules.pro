-keepattributes RuntimeVisibleAnnotations,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-allowaccessmodification
-repackageclasses ''

-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-keep class org.mozilla.thirdparty.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn org.mozilla.**

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

-dontnote **
