plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.deepsight.engine"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" } // test APK: same reason as :app
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.onnxruntime.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// Unit tests read <repo>/contracts/examples and write Kotlin-encoded copies for contracts/validate.py.
val contractsDir = rootDir.resolve("../contracts").canonicalPath
val contractOutDir = layout.buildDirectory.dir("contract-out").get().asFile.path
tasks.withType<Test>().configureEach {
    inputs.dir(contractsDir)
    outputs.dir(contractOutDir)
    systemProperty("contractsDir", contractsDir)
    systemProperty("contractOutDir", contractOutDir)
}
