plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    id("kotlin-kapt")
}

android {
    namespace = "dev.pdfforge"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.pdfforge"
        minSdk = 29
        targetSdk = 34
        versionCode = (project.findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: 1
        versionName = project.findProperty("VERSION_NAME") as String? ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "android"
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")?.takeIf { it.storeFile?.exists() == true }
                ?: signingConfigs.getByName("debug")
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
        }
    }
}

dependencies {
    implementation(project(":domain:models"))
    implementation(project(":domain:core"))
    implementation(project(":data:impl"))
    implementation(project(":data:storage"))
    implementation(project(":common:ui"))
    implementation(project(":common:utils"))
    implementation(project(":engine:mupdf"))
    implementation(project(":engine:converter"))
    implementation(project(":feature:home"))
    implementation(project(":feature:pdf_creation"))
    implementation(project(":feature:merge_split"))
    implementation(project(":feature:compression"))
    implementation(project(":feature:conversion"))

    implementation("com.tom-roush:pdfbox-android:2.0.27.0") // For PDFBoxResourceLoader.init in Application
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.hilt.android)
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.1.0")
    kapt(libs.hilt.compiler)
    kapt("androidx.hilt:hilt-compiler:1.1.0")
    implementation(libs.workmanager.ktx)
    implementation(libs.navigation.compose)
}
