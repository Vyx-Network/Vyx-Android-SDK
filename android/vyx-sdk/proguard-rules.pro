# Vyx SDK ProGuard Rules

# Keep public API
-keep public class com.vyx.sdk.VyxNode {
    public *;
}

-keep public class com.vyx.sdk.VyxConfig {
    public *;
}

# Keep service
-keep public class com.vyx.sdk.VyxService {
    public *;
}

# Keep JSON serialization for messages
-keepclassmembers class com.vyx.sdk.QuicClient$Message {
    *;
}

# Cronet
-keep class org.chromium.** { *; }
-dontwarn org.chromium.**

# Timber (if used)
-dontwarn org.jetbrains.annotations.**
