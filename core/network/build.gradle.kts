plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.openlist.client.core.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation(project(":core:common"))
    implementation(project(":core:auth"))
    implementation(project(":core:database"))

    implementation(libs.androidx.core.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // api, not implementation: OpenListApi's methods return retrofit2.Response/
    // okhttp3 types directly, so every module that calls it (data:repository)
    // needs these on its own compile classpath too.
    api(libs.squareup.retrofit)
    api(libs.squareup.okhttp)
    implementation(libs.squareup.retrofit.kotlinx.serialization.converter)
    implementation(libs.squareup.okhttp.logging.interceptor)

    // api: JsonElement is part of OpenListApi's mutation-endpoint return types
    // (mkdir/rename/remove/move/copy), for the same reason as Retrofit/OkHttp above.
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.squareup.mockwebserver)
}
