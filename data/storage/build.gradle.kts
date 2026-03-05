plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "dev.pdfforge.data.storage"
    compileSdk = 34

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":domain:models"))
    implementation(libs.datastore.prefs)
    implementation(libs.coroutines.android)
    
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
