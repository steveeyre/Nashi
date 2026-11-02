# kotlinx.serialization：保留 @Serializable 类的合成 serializer 与 Companion
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.nashi.**$$serializer { *; }
-keepclassmembers class com.nashi.** {
    *** Companion;
}
-keepclasseswithmembers class com.nashi.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor 客户端引擎通过类名反射实例化
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.debug.**
