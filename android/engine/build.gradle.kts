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

    // Golden tests read ml/tests/data at the asset root; <repo>/ml/packs is staged under packs/ (stageGoldenPacks).
    // ml/data/android_parity is local-only RBCNet parity data; the test skips when it is absent.
    sourceSets {
        named("androidTest") {
            assets.srcDir(rootDir.resolve("../ml/tests/data"))
            assets.srcDir(rootDir.resolve("../ml/data/android_parity"))
        }
    }
}

// Every pack's manifest, model and golden cases live in ml/packs; PackGoldenTest reads them from the test APK's
// assets under packs/<id>/ (the prefix keeps them out of the asset root that PackLoaderDeviceTest scans).
val stageGoldenPacks by tasks.registering(Sync::class) {
    from(rootDir.resolve("../ml/packs"))
    into(layout.buildDirectory.dir("golden-assets/packs"))
}
androidComponents {
    onVariants { variant ->
        variant.androidTest?.sources?.assets?.addStaticSourceDirectory(layout.buildDirectory.dir("golden-assets").get().asFile.path)
    }
}
tasks.configureEach { if (name.endsWith("AndroidTestAssets")) dependsOn(stageGoldenPacks) }

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.onnxruntime.android)
    implementation(libs.opencv)
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
