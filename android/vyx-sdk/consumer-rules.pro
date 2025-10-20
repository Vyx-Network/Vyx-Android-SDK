# Vyx SDK Consumer ProGuard Rules
# These rules are automatically applied to apps that use this AAR

# Keep all Vyx SDK classes
-keep class com.vyx.sdk.** { *; }
-keepclassmembers class com.vyx.sdk.** { *; }
-keepnames class com.vyx.sdk.** { *; }

# Preserve Serializable classes (VyxConfig needs this for Intent extras)
-keepclassmembers class com.vyx.sdk.VyxConfig implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep all fields in VyxConfig (required for serialization)
-keepclassmembers class com.vyx.sdk.VyxConfig {
    <fields>;
}

# Keep vyxclient (Go Mobile) classes
-keep class vyxclient.** { *; }
-keepclassmembers class vyxclient.** { *; }
-keepnames class vyxclient.** { *; }

# Keep Go Mobile Seq class - CRITICAL: JNI bridge used by native Go code
# This fixes "failed to find method Seq.getRef" crash in release builds
-keep class go.Seq { *; }
-keepclassmembers class go.Seq { *; }
-keep class go.** { *; }
-keepclassmembers class go.** { *; }
-keepnames class go.** { *; }

# Keep native methods for Go Mobile
-keepclasseswithmembernames class vyxclient.** {
    native <methods>;
}

# Kotlin Coroutines (required by Vyx SDK)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Don't warn about missing dependencies
-dontwarn com.vyx.sdk.**
-dontwarn vyxclient.**
-dontwarn go.**
