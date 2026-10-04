import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val localGitSha = providers.exec {
    commandLine("git", "-C", rootDir.absolutePath, "rev-parse", "--short", "HEAD")
}.standardOutput.asText.get().trim().ifBlank { "unknown" }
val gitSha = providers.environmentVariable("DEVSERVER_GIT_SHA").orElse(localGitSha).get()
val devServerVersionCode = providers.environmentVariable("DEVSERVER_VERSION_CODE").orNull?.toIntOrNull()
val tvFlowArmOnly = providers.environmentVariable("TVFLOW_ARM_ONLY").orNull == "1"
val tvFlowTargetAbi = providers.environmentVariable("TVFLOW_TARGET_ABI").orNull?.takeIf { it.isNotBlank() }
val devServerVersionName = providers.gradleProperty("TVFLOW_VERSION_NAME")
    .orElse(providers.environmentVariable("DEVSERVER_VERSION_NAME"))
    .orNull
    ?.takeIf { it.isNotBlank() }
val projectVersionName = rootProject.file("VERSION")
    .takeIf { it.isFile }
    ?.readText()
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: "0.0.0"
val gitCommitCount = providers.exec {
    commandLine("git", "-C", rootDir.absolutePath, "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toIntOrNull()?.coerceAtLeast(35) ?: 35
val tvFlowVersionCodeBase = 300_000_000
val appVersionCode = devServerVersionCode ?: (tvFlowVersionCodeBase + gitCommitCount)
val releaseKeystorePath = providers.environmentVariable("TVFLOW_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("TVFLOW_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("TVFLOW_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("TVFLOW_KEY_PASSWORD").orNull
val releaseSigningAvailable = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

extensions.configure<ApplicationExtension>("android") {
    namespace = "com.tvflow.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tvflow.app"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = devServerVersionName ?: projectVersionName
        buildConfigField("String", "AUTHOR_SIGNATURE", "\"by bobcatfr63\"")

        if (tvFlowTargetAbi != null) {
            ndk {
                abiFilters += listOf(tvFlowTargetAbi)
            }
        } else if (tvFlowArmOnly) {
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
        }
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    if (releaseSigningAvailable) {
        signingConfigs {
            create("tvflowRelease") {
                storeFile = file(checkNotNull(releaseKeystorePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BUILD_CHANNEL", "\"Dev\"")
        }
        release {
            buildConfigField("String", "BUILD_CHANNEL", "\"Stable\"")
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("tvflowRelease")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.3")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.tv:tv-foundation:1.0.0")
    implementation("androidx.tv:tv-material:1.1.0")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.room:room-paging:2.8.5")
    implementation("androidx.paging:paging-runtime-ktx:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")

    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.10.4")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("com.google.android.gms:play-services-cast-framework:22.3.1")
    implementation("org.videolan.android:libvlc-all:3.7.6")

    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
}
