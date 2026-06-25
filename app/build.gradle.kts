plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

import java.util.Properties

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun readLocalProp(key: String, default: String = ""): String =
    localProps.getProperty(key)?.trim()?.takeIf { it.isNotBlank() } ?: default

fun readEnvValue(key: String, default: String = ""): String {
    val envFile = rootProject.file(".env")
    if (!envFile.exists()) return default
    val line = envFile.readLines()
        .asSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$key=") } ?: return default
    return line.substringAfter("=")
}

val versionProps = Properties().apply {
    val file = rootProject.file("version.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

/** Single source for versionName — must stay aligned with GitHub release tags (e.g. tag V1.03 → 1.03). */
fun resolveVersionName(): String =
    (project.findProperty("VERSION_NAME") as String?)?.trim()?.takeIf { it.isNotBlank() }
        ?: readEnvValue("VERSION_NAME").takeIf { it.isNotBlank() }
        ?: versionProps.getProperty("VERSION_NAME")?.trim()?.takeIf { it.isNotBlank() }
        ?: error(
            "VERSION_NAME not configured. Set version.properties, .env VERSION_NAME=, " +
                "or pass -PVERSION_NAME=1.04 to Gradle."
        )

fun resolveVersionCode(versionName: String): Int =
    readEnvValue("VERSION_CODE").toIntOrNull()
        ?: versionProps.getProperty("VERSION_CODE")?.trim()?.toIntOrNull()
        ?: semverToVersionCode(versionName)

/** Maps semver (e.g. 2.1.0) to a monotonic versionCode — keep in sync with AppVersion.semverToVersionCode. */
fun semverToVersionCode(versionName: String): Int {
    val normalized = versionName.trim().removePrefix("v").removePrefix("V")
    val parts = normalized.split(".", "-", "_")
    val major = parts.getOrNull(0)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
    return major * 10_000 + minor * 100 + patch
}

android {
    namespace = "com.grid.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.grid.tv"
        minSdk = 23
        targetSdk = 34
        val appVersionName = resolveVersionName()
        versionName = appVersionName
        versionCode = resolveVersionCode(appVersionName)
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        buildConfigField("String", "TMDB_API_KEY", "\"${readEnvValue("TMDB_API_KEY")}\"")
        buildConfigField("String", "TMDB_BASE_URL", "\"${readEnvValue("TMDB_BASE_URL", "https://api.themoviedb.org/3")}\"")
        buildConfigField("String", "TMDB_IMAGE_BASE_URL", "\"${readEnvValue("TMDB_IMAGE_BASE_URL", "https://image.tmdb.org/t/p/")}\"")
        buildConfigField("String", "OPENSUBTITLES_API_KEY", "\"${readEnvValue("OPENSUBTITLES_API_KEY")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${readLocalProp("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${readLocalProp("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${readEnvValue("GOOGLE_WEB_CLIENT_ID")}\"")
        buildConfigField("String", "GITHUB_OWNER", "\"${readEnvValue("GITHUB_OWNER", "gridtvsupport-wq")}\"")
        buildConfigField("String", "GITHUB_REPO", "\"${readEnvValue("GITHUB_REPO", "GRID")}\"")
        manifestPlaceholders["supabaseAuthRedirectScheme"] = "com.grid.tv"
    }

    flavorDimensions += "store"
    productFlavors {
        create("google") {
            dimension = "store"
        }
        create("amazon") {
            dimension = "store"
        }
    }

    splits {
        abi {
            isEnable = false
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = readLocalProp("KEYSTORE_PATH")
            if (keystorePath.isNotBlank()) {
                storeFile = file(keystorePath)
                storePassword = readLocalProp("KEYSTORE_PASSWORD")
                keyAlias = readLocalProp("KEY_ALIAS")
                keyPassword = readLocalProp("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            installation {
                // Avoid INSTALL_BASELINE_PROFILE_FAILED on x86 emulators / sideload installs.
                enableBaselineProfile = false
            }
            val releaseSigning = signingConfigs.getByName("release")
            val keystoreFile = releaseSigning.storeFile
            if (keystoreFile == null || !keystoreFile.exists()) {
                throw GradleException(
                    "Release signing keystore missing. Set KEYSTORE_PATH (and passwords) in " +
                        "local.properties or CI secrets — debug signing must not ship to users."
                )
            }
            signingConfig = releaseSigning
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }

    configurations.configureEach {
        exclude(group = "net.sf.kxml", module = "kxml2")
    }
}

dependencies {
    configurations.configureEach {
        exclude(group = "net.sf.kxml", module = "kxml2")
    }

    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.datasource.rtmp)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.ui)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.material)

    implementation(libs.coil.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.gson)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.functions)
    implementation(libs.supabase.compose.auth)
    implementation(libs.ktor.client.android)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.googleid)
    implementation(libs.play.services.cast.framework)
    implementation(libs.nanohttpd)
    implementation(libs.zxing.core)

    testImplementation(libs.junit4)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.room.testing)
}
