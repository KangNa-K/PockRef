# R8 / ProGuard keep rules for PockRef Android.
# Applied to both debug and release builds (minify is on for both).

# ---------------------------------------------------------------------------
# Compose runtime — generated lambdas and stable types are accessed reflectively
# by the Compose compiler plugin. Keep the runtime and any @Composable members.
# ---------------------------------------------------------------------------
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.tooling.** { *; }
-keep class androidx.compose.ui.tooling.preview.** { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# Keep the @Preview annotation so that the preview tooling can find it.
-keep @androidx.compose.ui.tooling.preview.Preview class * { *; }
-keepattributes *Annotation*

# ---------------------------------------------------------------------------
# Kotlin metadata — required so reflection (KClass.java, primary constructors)
# still works after R8 strips synthetic accessors.
# ---------------------------------------------------------------------------
-keep class kotlin.Metadata { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations, AnnotationDefault

# Coroutines internals.
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ---------------------------------------------------------------------------
# PockRef domain model — data classes accessed by the binary .pur codec
# (PurSceneCodec.write/read). R8 must not rename their members or remove the
# primary constructors / copy() / componentN().
# ---------------------------------------------------------------------------
-keep class com.pockref.app.model.** { *; }
-keepclassmembers class com.pockref.app.model.** {
    <init>(...);
    *;
}

# PockRefAction sealed interface — destructured via exhaustive `when` blocks,
# which the compiler lowers to integer equality checks. The data object / data
# class subclasses must keep their generated `INSTANCE` field and constructors.
-keep class com.pockref.app.ui.PockRefAction { *; }
-keep class com.pockref.app.ui.PockRefAction$* { *; }
-keepclassmembers class com.pockref.app.ui.PockRefAction$* {
    public <init>(...);
}

# The .pur binary codec's companion-object constants (MAGIC, headers, …) are
# referenced by name via the byte-stream reader. Keep the whole class.
-keep class com.pockref.app.data.PurSceneCodec { *; }
-keep class com.pockref.app.data.PurSceneCodec$Companion { *; }
-keepclassmembers class com.pockref.app.data.PurSceneCodec$Companion {
    *;
}

# ---------------------------------------------------------------------------
# AndroidX ViewModel — instantiated by reflection from Lifecycle / Compose
# viewModel() factory. Public no-arg / Application-arg constructors must survive.
# ---------------------------------------------------------------------------
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}
-keep class * extends androidx.lifecycle.ViewModel$* { *; }

# LocalLifecycleOwner is consumed via CompositionLocal by
# `collectAsStateWithLifecycle` (see MainActivity.kt). R8 in release builds
# strips the provider chain without these keeps, causing
# `IllegalStateException: CompositionLocal LocalLifecycleOwner not present`
# on first launch. Keep both the androidx-lifecycle-compose definition and
# the androidx-compose-ui-platform integration.
-keep class androidx.lifecycle.compose.** { *; }
-keep class androidx.compose.ui.platform.** { *; }
-keep class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt { *; }

# ---------------------------------------------------------------------------
# BuildConfig — generated constants read by the binary codec and logging.
# ---------------------------------------------------------------------------
-keep class com.pockref.app.BuildConfig { *; }

# ---------------------------------------------------------------------------
# Suppress warnings for known missing types pulled in by transitive deps that
# PockRef does not actually use. These are not errors — only notes from R8.
# ---------------------------------------------------------------------------
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn org.jetbrains.annotations.**
