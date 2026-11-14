-repackageclasses 'o'
-allowaccessmodification
-overloadaggressively
-renamesourcefileattribute ""
-optimizationpasses 5

-keep public class com.bw.cardashboard.MainActivity {
    public <init>();
}
-keep public class com.bw.cardashboard.FloatingService {
    public <init>();
}

-keep public class com.bw.cardashboard.ui.* extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-dontwarn bw.car.**
-dontwarn android.**
