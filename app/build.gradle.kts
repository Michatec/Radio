plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    id("kotlin-parcelize")
}

android {
    namespace = "com.michatec.radio"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.michatec.radio"
        minSdk = 28
        targetSdk = 37
        versionCode = 151
        versionName = "15.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            isCrunchPngs = false
            proguardFiles.addAll(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), file("proguard-rules.pro")))
            applicationIdSuffix = ".debug"
            buildConfigField("boolean", "IS_DEBUG_ENABLED", "true")
        }

        release {
            isMinifyEnabled = false
            isShrinkResources = false
            isCrunchPngs = true
            proguardFiles.addAll(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), file("proguard-rules.pro")))
            buildConfigField("boolean", "IS_DEBUG_ENABLED", "false")
        }
    }

    ndkVersion = "29.0.14206865"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    // Google Stuff //
    implementation(libs.material)
    implementation(libs.gson)
    implementation(libs.play.services.cast.framework)

    // AndroidX Stuff //
    implementation(libs.core.ktx)
    implementation(libs.activity.ktx)
    implementation(libs.palette.ktx)
    implementation(libs.preference.ktx)
    implementation(libs.media)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.session)
    implementation(libs.media3.cast)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.work.runtime.ktx)
    implementation(libs.leanback)

    implementation(libs.freedroidwarn)

    // Ktor //
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.serialization.gson)

    // Volley HTTP request //
    implementation(libs.volley)
    implementation(libs.material3)
}
