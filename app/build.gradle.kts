plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "dev.pdfforge"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.pdfforge"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":domain:models"))
    implementation(project(":domain:core"))
    implementation(project(":data:impl"))
    implementation(project(":data:storage"))
    implementation(project(":data:worker"))
    implementation(project(":common:ui"))
    implementation(project(":common:utils"))
    
    // Engine Modules
    implementation(project(":engine:mupdf"))
    implementation(project(":engine:converter"))
    
    // Feature Modules
    implementation(project(":feature:home"))
    implementation(project(":feature:pdf_creation"))
    implementation(project(":feature:merge_split"))
    implementation(project(":feature:compression"))
    implementation(project(":feature:conversion"))

    // Compose with BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    
    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    
    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.ext.work)
    kapt(libs.hilt.ext.compiler)
    implementation(libs.navigation.compose)
    implementation(libs.coil.compose)
}
