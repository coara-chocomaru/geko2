-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-allowaccessmodification
-repackageclasses ''

-dontwarn org.yaml.snakeyaml.scanner.ScannerImpl

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
