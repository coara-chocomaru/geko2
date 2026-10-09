-keepattributes RuntimeVisibleAnnotations,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-allowaccessmodification
-repackageclasses ''

-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-keep class org.mozilla.thirdparty.** { *; }
-dontwarn org.mozilla.**

-keep class com.coara.browser2.gecko.** { *; }

-keep,allowoptimization public class com.coara.browser2.BrowserApplication {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.MainActivity {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.SecretActivity {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.grepmd5appActivity {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.exec {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.htmlview {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.util.DownloadFallbackService {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.util.DownloadCompletionReceiver {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.DownloadOpenActivity {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.DownloadHistoryActivity {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.pagedl {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.num {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.txtphoto {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.asciiart {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.QrCodeActivity {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.plugin.PluginManagerActivity {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.SettingsActivity {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.TouchpadActivity {
    public <init>(...);
}

-keep,allowoptimization public class com.coara.browser2.SecretTouchpadActivity {
    public <init>(...);
}
