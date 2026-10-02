plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.deepsight.report"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
        ndk { abiFilters += "arm64-v8a" } // same reason as :app
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// litertlm-android 0.17.1 and its kotlin-reflect are Kotlin 2.4 binaries; the project compiles with Kotlin 2.2.
// Skipping the metadata version check only here keeps LiteRT-LM's types inside this module. Upgrading the project
// to Kotlin 2.4 is the proper fix (team decision, shared libs.versions.toml).
kotlin {
    compilerOptions { freeCompilerArgs.add("-Xskip-metadata-version-check") }
}

dependencies {
    // The report reads the engine's contract types; it never computes or changes triage (AGENTS.md).
    implementation(project(":engine"))
    // Gemma narrates the report; it never decides triage (AGENTS.md). The .litertlm model is not in the APK.
    implementation(libs.litertlm.android)
    testImplementation(libs.junit)
}
