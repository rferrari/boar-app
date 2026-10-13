plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "team.sopa.boar"
    compileSdk = 36
    // The NDK's strip tool for the engine's libraries: without it they ship with their debug symbols (77 MB libllama-common).
    ndkVersion = "27.1.12297006"

    // The Supabase URL and publishable key the Expo app reads (EXPO_PUBLIC_* in the repo's .env, never committed).
    // Without them the build has no Share button, as the Expo app.
    val env = rootProject.file("../.env").takeIf { it.isFile }?.readLines().orEmpty()
        .mapNotNull { l -> l.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim().trim('"') } }.toMap()

    // FLAG: the Truffle Vault's boars as the launcher icon (src/launcherIcons: activity aliases and icons). Off by default:
    // the boars stay inside the app. On with boar.truffleLauncherIcons=true (gradle.properties or -P) or
    // BOAR_TRUFFLE_LAUNCHER_ICONS=true (the repo's .env or the environment).
    val truffleLauncherIcons = (providers.gradleProperty("boar.truffleLauncherIcons").orNull
        ?: env["BOAR_TRUFFLE_LAUNCHER_ICONS"] ?: System.getenv("BOAR_TRUFFLE_LAUNCHER_ICONS"))?.trim()?.toBoolean() ?: false
    // You read the build file: there's an EXPERIMENTAL block at the bottom of Advanced tweaks (Boar Labs, Boar Avatars),
    // shown only in builds with EXPERIMENTAL_FEATURES=true in the repo's .env (or the environment, or -Pboar.experimentalFeatures=true).
    // Without it the switches never show and both stay off. The image lab's sd-cli (labs/jniLibs, from
    // scripts/build-sd-cli.sh, about 31 MB) ships only with it, unless boar.labs.imageEngine=false.
    val experimental = (providers.gradleProperty("boar.experimentalFeatures").orNull ?: env["EXPERIMENTAL_FEATURES"] ?: System.getenv("EXPERIMENTAL_FEATURES"))?.trim()?.toBoolean() ?: false
    val labsImageEngine = experimental && (providers.gradleProperty("boar.labs.imageEngine").orNull?.trim()?.toBoolean() ?: true)
    // Uncensored and adult models (HfQuery.uncensored) are hidden from Discover GGUF models unless ALLOW_UNCENSORED_MODELS=true
    // (.env, the environment, or -Pboar.allowUncensoredModels=true).
    val allowUncensored = (providers.gradleProperty("boar.allowUncensoredModels").orNull ?: env["ALLOW_UNCENSORED_MODELS"] ?: System.getenv("ALLOW_UNCENSORED_MODELS"))?.trim()?.toBoolean() ?: false
    defaultConfig {
        buildConfigField("boolean", "ALLOW_UNCENSORED_MODELS", "$allowUncensored")
        buildConfigField("boolean", "TRUFFLE_LAUNCHER_ICONS", "$truffleLauncherIcons")
        buildConfigField("boolean", "EXPERIMENTAL_FEATURES", "$experimental")
        // Lab ids to leave out of the grid (labs/LabPlugin.kt), e.g. boar.labs.hide=bigBrain,signboard.
        val labsHide = providers.gradleProperty("boar.labs.hide").orNull ?: ""
        buildConfigField("String", "LABS_HIDE", "\"${labsHide.replace("\"", "")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${env["EXPO_PUBLIC_SUPABASE_URL"] ?: ""}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${env["EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY"] ?: ""}\"")
        // The release keeps the Expo app's id and key, so it installs over v1.1.x with its models.
        applicationId = "team.sopa.aoair"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "2.0.0-dev"
        ndk { abiFilters += listOf("arm64-v8a") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // The Expo app's upload key, named in ~/.gradle/gradle.properties (never in the repo), as plugins/withReleaseSigning.js
    // reads it. Without it the release is debug-signed, which can't install over a store or release build of BOAR.
    val storeFile = providers.gradleProperty("BOAR_UPLOAD_STORE_FILE").orNull
    signingConfigs {
        if (storeFile != null) create("release") {
            this.storeFile = file(storeFile)
            storePassword = providers.gradleProperty("BOAR_UPLOAD_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("BOAR_UPLOAD_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("BOAR_UPLOAD_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        debug {
            // Development builds install beside the current BOAR, never over it.
            applicationIdSuffix = ".native"
            versionNameSuffix = "-native"
        }
        // A throwaway install for the on-device tests that wipe data (first-run setup, Purge All Data), so they never
        // touch the debug app someone is using.
        create("devtest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".devtest"
            versionNameSuffix = "-devtest"
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    // Instrumented tests run against the throwaway devtest install (./gradlew :app:assembleDevtestAndroidTest).
    testBuildType = "devtest"
    packaging { jniLibs { useLegacyPackaging = true } }
    if (truffleLauncherIcons) {
        // Merged over main for every build type: the launcher entry moves to the aliases.
        for (type in listOf("debug", "release", "devtest")) sourceSets[type].manifest.srcFile("src/launcherIcons/AndroidManifest.xml")
        sourceSets["main"].res.srcDir("src/launcherIcons/res")
    }
    sourceSets["main"].java.srcDir("src/labs/java")
    sourceSets["main"].res.srcDir("src/labs/res")
    // sd-cli for the image lab, as libsdcli*.so so it's installed executable in nativeLibraryDir (gitignored).
    if (labsImageEngine) sourceSets["main"].jniLibs.srcDir("../labs/jniLibs")
}

kotlin { jvmToolchain(17) }

// Data the Expo app ships in the repo's assets/, copied at build time so it stays in one place: the Portuguese name
// lexicon (assets/lexicon/pt-en.json) and the bundled corpus (assets/corpus).
val sharedAssets = layout.buildDirectory.dir("generated/sharedAssets")
val copySharedAssets by tasks.registering(Copy::class) {
    from(rootProject.file("../assets")) {
        include("lexicon/pt-en.json", "corpus/*.json")
    }
    into(sharedAssets)
}
android.sourceSets["main"].assets.srcDir(sharedAssets)
tasks.named("preBuild") { dependsOn(copySharedAssets) }

dependencies {
    implementation(project(":engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // PDF text for document import (Apache-2.0).
    implementation(libs.pdfbox.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    // On-device checks (the PIN, its Keystore key, the sealed database, the PIN screens), run with adb am instrument.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    "devtestImplementation"(libs.compose.ui.test.manifest)
}
