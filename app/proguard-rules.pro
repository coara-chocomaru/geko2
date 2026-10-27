-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions
-renamesourcefileattribute SourceFile
-dontrepackage

-keep,allowshrinking,allowoptimization class androidx.appcompat.widget.ActionMenuPresenter$OverflowMenuButton

-dontwarn org.yaml.snakeyaml.scanner.ScannerImpl

-maximumremovedandroidloglevel 7

-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}

-assumenosideeffects class com.coara.browser2.plugin.PluginLogger {
    void log(...);
    java.lang.String readTail(int);
    java.lang.String readFor(java.lang.String,int);
}

-assumevalues class com.coara.browser2.plugin.PluginLogger {
    java.lang.String readTail(int) return "";
    java.lang.String readFor(java.lang.String,int) return "";
}

-assumenosideeffects class com.coara.browser2.BrowserApplication {
    private void writeCrashLog(java.lang.Thread,java.lang.Throwable);
}
