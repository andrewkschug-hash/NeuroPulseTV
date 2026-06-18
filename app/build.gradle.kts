plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

fun readEnvValue(key: String, default: String = ""): String {
    val envFile = rootProject.file(".env")
    if (!envFile.exists()) return default
    val line = envFile.readLines()
        .asSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$key=") } ?: return default
    return line.substringAfter("=")
}

android {
    namespace = "com.grid.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.grid.tv"
        minSdk = 23
        targetSdk = 34
        versionCode = 2
        versionName = "2.1.0"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
        buildConfigField("String", "TMDB_API_KEY", "\"${readEnvValue("TMDB_API_KEY")}\"")
        buildConfigField("String", "TMDB_BASE_URL", "\"${readEnvValue("TMDB_BASE_URL", "https://api.themoviedb.org/3")}\"")
        buildConfigField("String", "TMDB_IMAGE_BASE_URL", "\"${readEnvValue("TMDB_IMAGE_BASE_URL", "https://image.tmdb.org/t/p/")}\"")
        buildConfigField("String", "OPENSUBTITLES_API_KEY", "\"${readEnvValue("OPENSUBTITLES_API_KEY")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${readEnvValue("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${readEnvValue("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${readEnvValue("GOOGLE_WEB_CLIENT_ID")}\"")
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
        // CRITICAL: Back up keystore + passwords securely. Losing them blocks updates for existing installs.
        create("release") {
            storeFile = file("keystore/release.jks")
            storePassword = "TODO_STORE_PASSWORD"
            keyAlias = "TODO_KEY_ALIAS"
            keyPassword = "TODO_KEY_PASSWORD"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
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
    implementation(libs.androidx.media3.ui)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

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
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
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
