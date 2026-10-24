-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-repackageclasses ''

-keep,allowshrinking,allowoptimization class androidx.appcompat.widget.ActionMenuPresenter$OverflowMenuButton
-keep,allowshrinking,allowoptimization class com.coara.browser2.MainActivity
-keep,allowshrinking,allowoptimization class com.coara.browser2.plugin.**
-keep,allowshrinking,allowoptimization class com.coara.browser2.QrCodeActivity
-keep,allowshrinking,allowoptimization class com.coara.browser2.pagedl
-keep,allowshrinking,allowoptimization class com.coara.browser2.asciiart
-keep,allowshrinking,allowoptimization class com.coara.browser2.txtphoto

-dontwarn org.yaml.snakeyaml.scanner.ScannerImpl

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
