plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.pdfforge.data.storage"
    compileSdk = 34

    defaultConfig {
        minSdk = 29
    }
}

dependencies {
    implementation(libs.datastore.prefs)
    implementation(libs.coroutines.android)
}
