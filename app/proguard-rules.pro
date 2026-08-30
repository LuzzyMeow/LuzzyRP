# LuzzyRP R8 混淆规则

# ---- kotlinx.serialization：序列化器保留（@Serializable 模型不可混淆字段名） ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.luzzymeow.luzzyrp.**$$serializer { *; }
-keepclassmembers class com.luzzymeow.luzzyrp.** {
    *** Companion;
}
-keepclasseswithmembers class com.luzzymeow.luzzyrp.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- OkHttp / Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ---- Coroutines ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ---- Room（KSP 生成的实现类） ----
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
