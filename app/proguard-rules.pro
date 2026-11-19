# ProGuard/R8 rules for the release build.
#
# The launcher is almost entirely Kotlin + Compose, both of which ship their own consumer
# rules, so only the places where a class is reached by name rather than by reference need
# help here.

# Keep line numbers so release crash reports are actually actionable. The file name is
# still rewritten, so the source tree layout is not leaked.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- OkHttp ---------------------------------------------------------------------------
# OkHttp probes for optional TLS providers (Conscrypt, BouncyCastle, OpenJSSE) that are
# not bundled with the app. Its consumer rules already guard the lookups; silencing the
# missing-class warnings keeps the release build output readable.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Room -----------------------------------------------------------------------------
# Room loads the generated `AppDatabase_Impl` by name from `Room.databaseBuilder`, so the
# implementation is only reachable as a string. Room's own rules keep RoomDatabase
# subclasses; the generated implementation is kept here explicitly.
-keep class com.example.data.local.AppDatabase_Impl { *; }

# --- Accessibility service ------------------------------------------------------------
# The system binds this service from the manifest and reads its XML metadata. Keeping it
# explicitly also preserves the service callbacks the platform invokes.
-keep class com.example.HomeOverrideService { *; }

# --- Enum names read from preferences ------------------------------------------------
# Enum values are restored from SharedPreferences and sync payloads by name
# (`ThemeStyle.valueOf`, `FavoritesLayout.valueOf`, `GameType.valueOf`, ...). R8 rewrites
# enum access for constants referenced in code, but keeping `values`/`valueOf` documents
# and protects that contract.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
