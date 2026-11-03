plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

import com.android.build.api.dsl.ApkSigningConfig
import java.io.File
import java.util.Properties

android {
    namespace = "com.pockref.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pockref.app"
        minSdk = 24
        targetSdk = 34

        versionCode = 46
        versionName = "2.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Release signing is loaded from a `keystore.properties` (gitignored)
    // that contains:
    //   storeFile=release.keystore
    //   storePassword=...
    //   keyAlias=pockref
    //   keyPassword=...
    //
    // Two locations are checked, in order:
    //   1. `~/.android-keys/pockref/keystore.properties` — preferred, keeps
    //      the keystore outside the project tree (so a `git clone` on
    //      another machine that lacks the key still builds a debug APK
    //      without ever seeing the production signing material).
    //   2. `<projectRoot>/keystore.properties` — fallback for older
    //      setups / CI that drops the file in the project.
    //
    // `storeFile` may be an absolute path or a path relative to the
    // rootProject (which is what the original code did).
    //
    // To rotate: `keytool -genkey ... -keystore release.keystore` and
    // update the properties file. NEVER commit the properties or the
    // keystore.
    val userHome = System.getProperty("user.home")
    val keystoreProps = listOf(
        file("$userHome/.android-keys/pockref/keystore.properties"),
        rootProject.file("keystore.properties"),
    ).firstOrNull { it.exists() }
    val signingConfigRelease: ApkSigningConfig? = if (keystoreProps != null) {
        val props = Properties()
        keystoreProps.inputStream().use { props.load(it) }
        val storeFilePath = props.getProperty("storeFile")
        val rawStoreFile: File? = storeFilePath?.let { File(it) }
        val resolvedStoreFile: File? = when {
            rawStoreFile == null -> null
            rawStoreFile.isAbsolute -> rawStoreFile
            else -> rootProject.file(storeFilePath)
        }
        signingConfigs.create("release") {
            if (resolvedStoreFile != null) {
                storeFile = resolvedStoreFile
            }
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else {
        null
    }

    buildTypes {
        debug {
            // Debug builds stay readable for Android Studio attach / logcat /
            // debugger; running R8 here caused the install-and-crash on first
            // launch because reflection-driven Compose init paths weren't
            // fully covered by the keep rules. R8 is validated in release.
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (signingConfigRelease != null) {
                signingConfig = signingConfigRelease
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    // Version comes from the Compose BOM (adaptive 1.3.0 in BOM 2026.09.00).
    implementation("androidx.compose.material3.adaptive:adaptive")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
}
