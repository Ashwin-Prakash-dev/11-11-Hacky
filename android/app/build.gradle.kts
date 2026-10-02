plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.deepsight"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.deepsight"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Demo phones are arm64; ONNX Runtime's native libs for all 4 ABIs would add ~129 MB uncompressed.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

/** Copies <repo>/ml/packs, without golden test data, to packs/ in the APK assets, where PackLoader looks. */
abstract class StagePacks : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packs: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun stage() {
        // Only finished packs: a directory without manifest.json (a module still being added) stays out.
        val ready = packs.get().asFile.listFiles().orEmpty().filter { it.resolve("manifest.json").isFile }
        files.sync {
            ready.forEach { pack ->
                from(pack) {
                    exclude("**/golden/**")
                    into("packs/${pack.name}")
                }
            }
            into(output)
        }
    }
}

val stagePacks = tasks.register<StagePacks>("stagePacks") {
    packs.set(rootDir.resolve("../ml/packs"))
    output.set(layout.buildDirectory.dir("generated/packAssets"))
}

/** Test APK only: the frozen contract examples (ResultScreenTest) and the engine's smoke pack under testpacks/ (CaseRunnerTest). */
abstract class StageTestAssets : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val examples: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val smokePack: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun stage() {
        files.sync {
            from(examples)
            from(smokePack) { into("testpacks/smoke") }
            into(output)
        }
    }
}

val stageTestAssets = tasks.register<StageTestAssets>("stageTestAssets") {
    examples.set(rootDir.resolve("../contracts/examples"))
    smokePack.set(rootDir.resolve("engine/src/androidTest/assets/smoke"))
    output.set(layout.buildDirectory.dir("generated/testAssets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(stagePacks, StagePacks::output)
        variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(stageTestAssets, StageTestAssets::output)
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.kotlinx.serialization.json) // :engine exposes Contracts.json but keeps this as implementation
    implementation(project(":report"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}