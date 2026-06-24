# Kotlin metadata (data classes, coroutines, serialization)
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Room entities, DAOs, and database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep class com.grid.tv.data.db.** { *; }
-dontwarn androidx.room.paging.**

# Paging 3 — keep Room paging sources for release VOD grids
-keep class * extends androidx.paging.PagingSource { *; }
-keep class com.grid.tv.data.db.dao.** { *; }

# App models / DTOs (Retrofit responses, kotlinx.serialization, data classes)
-keep @kotlinx.serialization.Serializable class ** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    <fields>;
}
-keep class com.grid.tv.data.remote.** { *; }
-keepclassmembers class * {
    *** copy(...);
    *** component*();
}

# Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Supabase / Ktor / kotlinx.serialization (used by auth)
-dontwarn io.ktor.**
-dontwarn kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }

# Google Cast
-keep class com.google.android.gms.cast.** { *; }
-keep class com.google.android.gms.cast.framework.** { *; }
-dontwarn com.google.android.gms.cast.**

# kxml2 / XmlPullParser conflict with Android SDK
-dontwarn org.xmlpull.v1.**
-keep class org.xmlpull.v1.** { *; }
-dontwarn org.kxml2.**
-keep class org.kxml2.** { *; }
