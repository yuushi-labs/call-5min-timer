import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/**
 * 署名。
 *
 * Release APK は「同じ鍵で署名されていないと上書きインストールできない」ため、
 * アプリ内アップデートで自分自身を更新できるよう、リポジトリに置いた固定の
 * 配布用鍵で毎回署名する。この鍵は配布 ID（公開情報）であり秘密情報ではない
 * （android/keystore/README.md を参照）。
 */
val distributionKeystore = System.getenv("CALL5_KEYSTORE_PATH")?.let { file(it) }
    ?: rootProject.file("keystore/call5min-timer.jks")
val signingPropertiesFile = rootProject.file("keystore/signing.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.exists()) {
        signingPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.yuushilabs.call5mintimer"
    compileSdk = 35
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.yuushilabs.call5mintimer"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        resourceConfigurations += listOf("ja", "en")
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        getByName("debug") {
            if (distributionKeystore.exists()) {
                storeFile = distributionKeystore
                storePassword = System.getenv("CALL5_KEYSTORE_PASSWORD")
                    ?: signingProperties.getProperty("storePassword", "android")
                keyAlias = System.getenv("CALL5_KEY_ALIAS")
                    ?: signingProperties.getProperty("keyAlias", "call5min")
                keyPassword = System.getenv("CALL5_KEY_PASSWORD")
                    ?: signingProperties.getProperty("keyPassword", "android")
            }
        }
        create("distribution") {
            if (distributionKeystore.exists()) {
                storeFile = distributionKeystore
                storePassword = System.getenv("CALL5_KEYSTORE_PASSWORD")
                    ?: signingProperties.getProperty("storePassword", "android")
                keyAlias = System.getenv("CALL5_KEY_ALIAS")
                    ?: signingProperties.getProperty("keyAlias", "call5min")
                keyPassword = System.getenv("CALL5_KEY_PASSWORD")
                    ?: signingProperties.getProperty("keyPassword", "android")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                if (distributionKeystore.exists()) signingConfigs.getByName("distribution")
                else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = false
        disable += setOf(
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "OldTargetApi",
            "MissingTranslation",
            "UnusedResources",
            "NewerVersionAvailable",
        )
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
