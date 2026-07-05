plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.openlist.client.feature.admin"
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

    buildFeatures {
        compose = true
    }
}

// v0.5_EXECUTION_PLAN.md §5.1 / S1-T1: dependency list is deliberately written
// out here (not just left implicit in the block below) so future Sprints
// can't silently widen this module's scope. `:feature:admin` depends ONLY on:
//   core:common, core:model, core:domain, core:designsystem
// It must NOT depend on:
//   - any other :feature:* module (no cross-feature Composable/ViewModel calls;
//     task-center reuse happens only via core:model/core:domain/core:designsystem)
//   - :data:repository (Hilt wires the real Impl at the :app graph level)
//   - :core:network (no direct Retrofit/DTO usage from UI)
//   - :core:database (no direct DAO usage from UI)
// v0.4 S5 previously regressed on this rule and had to be reworked; this
// comment is the guardrail for v0.5.
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
