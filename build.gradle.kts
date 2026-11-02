plugins {
    id("com.android.application") version "9.3.2" apply false
    // AGP 9 compiles Kotlin itself; this pins the Kotlin Gradle plugin
    // version it uses. Modules do not apply it directly.
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
