# Add project specific ProGuard rules here.

# Gson / JSON models used via reflection
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-dontwarn sun.misc.**
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.ambiback.manager.data.model.** { <fields>; }
-keep class com.ambiback.manager.data.local.PersistedDevice { <fields>; }
-keep class com.ambiback.manager.data.usb.UsbDeviceInfo { <fields>; }

# Retrofit service interfaces (reflective proxy)
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking interface com.ambiback.manager.data.network.DeviceApiService
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# OkHttp / Okio platform stubs
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn javax.annotation.**

# Hilt / Dagger keep what the AGP + Hilt Gradle plugins already expect;
# do not blanket-keep the entire dagger/javax packages.
-dontwarn dagger.hilt.**
-dontwarn javax.inject.**
