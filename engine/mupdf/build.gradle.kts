plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "dev.pdfforge.engine.mupdf"
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
    implementation(project(":domain:core"))
    implementation(project(":data:impl"))
    implementation(project(":data:storage"))

    implementation(libs.mupdf)
    
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
