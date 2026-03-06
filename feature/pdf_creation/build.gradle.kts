plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    id("kotlin-kapt")
}

android {
    namespace = "dev.pdfforge.feature.pdf_creation"
    compileSdk = 34

    defaultConfig {
        minSdk = 29
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":domain:models"))
    implementation(project(":domain:core"))
    implementation(project(":common:ui"))
    implementation(project(":common:utils"))
    implementation(project(":data:worker"))
    
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.navigation.compose)
    implementation(libs.workmanager.ktx)
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("androidx.core:core-ktx:1.13.1")
}
