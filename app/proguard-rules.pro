# Keep annotations and generic signatures used by the Ads and UMP SDKs.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Google Mobile Ads SDK.
-keep class com.google.android.gms.ads.** { *; }
-keep interface com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.**

# Google User Messaging Platform (UMP) SDK.
-keep class com.google.android.ump.** { *; }
-keep interface com.google.android.ump.** { *; }

# Room instantiates each database's generated implementation class by name and calls
# its DatabaseConfiguration constructor reflectively. R8 sees neither call, so it
# strips the constructor and the app dies inside androidx.startup before the first
# Activity is created. The Ads SDK pulls WorkManager (and therefore Room) in
# transitively, so this database is created on every launch.
-keep class * extends androidx.room.RoomDatabase { *; }

# Strip verbose, debug, and info logging from release builds.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
