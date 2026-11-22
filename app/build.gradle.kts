import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use { load(it) }
    }
}

fun resolveBuildSecret(vararg keys: String): String? {
    for (key in keys) {
        val fromProject = (project.findProperty(key) as String?)?.takeIf { it.isNotBlank() }
        if (fromProject != null) return fromProject
        val fromLocal = localProps.getProperty(key)?.takeIf { it.isNotBlank() }
        if (fromLocal != null) return fromLocal
        val fromEnv = System.getenv(key)?.takeIf { it.isNotBlank() }
        if (fromEnv != null) return fromEnv
    }
    return null
}

// AdMob IDs: Check GitHub Secrets / environment variables / Gradle properties first,
// and fallback to Google's official public test IDs when absent.
val googleTestAppId = "ca-app-pub-3940256099942544~3347511713"
val googleTestBannerUnitId = "ca-app-pub-3940256099942544/6300978111"
val googleTestInterstitialUnitId = "ca-app-pub-3940256099942544/8691691433"

val admobAppId: String = resolveBuildSecret(
    "admobAppId",
    "ADMOB_APP_ID",
    "GOOGLE_ADMOB_APP_ID",
) ?: googleTestAppId

// Banner units must be resolved from banner-specific keys only. Falling back to a native
// unit makes a 320x50 AdView request a native-format unit, which never fills.
val admobBannerUnitId: String = resolveBuildSecret(
    "admobBannerUnitId",
    "ADMOB_BANNER_UNIT_ID",
    "BANNER_AD_UNIT_ID",
    "BANNER_AD_ID",
) ?: googleTestBannerUnitId

val admobNativeUnitId: String = admobBannerUnitId

val admobInterstitialVideoUnitId: String = resolveBuildSecret(
    "admobInterstitialVideoUnitId",
    "ADMOB_INTERSTITIAL_VIDEO_UNIT_ID",
    "ADMOB_FULLSCREEN_AD_UNIT_ID",
    "FULLSCREEN_AD_UNIT_ID",
    "FULLSCREEN_AD_ID",
    "INTERSTITIAL_AD_UNIT_ID",
) ?: "ca-app-pub-3940256099942544/8691691433"

// Optional upload keystore configuration for Play App Signing.
val uploadStoreFile: String? = resolveBuildSecret("uploadStoreFile", "KEYSTORE_FILE")
val uploadStorePassword: String? = resolveBuildSecret("uploadStorePassword", "KEYSTORE_PASSWORD")
val uploadKeyAlias: String? = resolveBuildSecret("uploadKeyAlias", "KEY_ALIAS")
val uploadKeyPassword: String? = resolveBuildSecret("uploadKeyPassword", "KEY_PASSWORD")
val hasReleaseSigning: Boolean = !uploadStoreFile.isNullOrBlank() &&
    !uploadStorePassword.isNullOrBlank() &&
    !uploadKeyAlias.isNullOrBlank() &&
    !uploadKeyPassword.isNullOrBlank()

fun escapeBuildConfigString(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.studyapp.pomodoro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.studyapp.pomodoro"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "NATIVE_ADS_ENABLED", "true")

        ndk {
            debugSymbolLevel = "FULL"
        }

        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField(
            "String",
            "ADMOB_BANNER_UNIT_ID",
            "\"${escapeBuildConfigString(admobBannerUnitId)}\"",
        )
        buildConfigField(
            "String",
            "ADMOB_NATIVE_UNIT_ID",
            "\"${escapeBuildConfigString(admobNativeUnitId)}\"",
        )
        buildConfigField(
            "String",
            "ADMOB_INTERSTITIAL_VIDEO_UNIT_ID",
            "\"${escapeBuildConfigString(admobInterstitialVideoUnitId)}\"",
        )
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(uploadStoreFile!!)
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "NATIVE_ADS_ENABLED", "true")
            // Developer and tester devices must never generate production ad traffic, even
            // when local.properties carries the real AdMob IDs. Debug always uses Google's
            // public test app ID and units.
            manifestPlaceholders["admobAppId"] = googleTestAppId
            buildConfigField(
                "String",
                "ADMOB_BANNER_UNIT_ID",
                "\"$googleTestBannerUnitId\"",
            )
            buildConfigField(
                "String",
                "ADMOB_NATIVE_UNIT_ID",
                "\"$googleTestBannerUnitId\"",
            )
            buildConfigField(
                "String",
                "ADMOB_INTERSTITIAL_VIDEO_UNIT_ID",
                "\"$googleTestInterstitialUnitId\"",
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    lint {
        disable += "InvalidFragmentVersionForActivityResult"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

gradle.taskGraph.whenReady {
    // Match the tasks the caller actually requested, not the whole graph: lintRelease
    // pulls assembleRelease in transitively (vital lint needs the packaged artifact)
    // but is an analysis task, so it must not demand keystore or AdMob secrets.
    val isReleaseTask = gradle.startParameter.taskNames.any { taskName ->
        taskName.contains("Release", ignoreCase = true) &&
            (taskName.contains("bundle", ignoreCase = true) ||
                taskName.contains("assemble", ignoreCase = true) ||
                taskName.contains("package", ignoreCase = true))
    }
    if (isReleaseTask) {
        if (!hasReleaseSigning) {
            throw GradleException(
                "Release build requested without upload-keystore credentials. " +
                    "Supply uploadStoreFile/uploadStorePassword/uploadKeyAlias/uploadKeyPassword " +
                    "(KEYSTORE_FILE/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD) via local.properties, " +
                    "Gradle properties, or env so the AAB is signed.",
            )
        }
        if (admobAppId.startsWith("ca-app-pub-3940256099942544") ||
            admobBannerUnitId.startsWith("ca-app-pub-3940256099942544") ||
            admobNativeUnitId.startsWith("ca-app-pub-3940256099942544") ||
            admobInterstitialVideoUnitId.startsWith("ca-app-pub-3940256099942544")
        ) {
            throw GradleException(
                "Release build requested with Google public test AdMob IDs. " +
                    "Supply admobAppId (ADMOB_APP_ID), admobBannerUnitId (ADMOB_BANNER_UNIT_ID) and " +
                    "admobInterstitialVideoUnitId (ADMOB_INTERSTITIAL_VIDEO_UNIT_ID) so the AAB " +
                    "embeds production units.",
            )
        }
    }
}
