plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.openlist.client.core.model"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
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
    // v0.5_EXECUTION_PLAN.md §11 S3-T1/P-507: AdminUserPage/AdminStoragePage need
    // to be @Serializable so `admin_cache.rawJson` can store the *domain model*
    // (never the raw DTO) — this is the only reason core:model needs
    // kotlinx.serialization at all (no other model in this module is @Serializable).
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
