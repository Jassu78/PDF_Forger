pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PDF Forger"

// App (main runnable module)
include(":app")

// Domain Modules
include(":domain:models")
include(":domain:core")

// Common Modules
include(":common:ui")
include(":common:utils")

// Data Modules
include(":data:storage")
include(":data:impl")
include(":data:worker")

// Engine Modules
include(":engine:mupdf")
include(":engine:converter")

// Feature Modules
include(":feature:home")
include(":feature:pdf_creation")
include(":feature:merge_split")
include(":feature:compression")
include(":feature:conversion")
