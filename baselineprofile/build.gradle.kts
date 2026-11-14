// Generates the app's own Baseline Profile (see BaselineProfileGenerator) and runs the startup
// benchmark. Needs a connected phone:
//   ./gradlew :androidApp:generateReleaseBaselineProfile
// The profile lands in androidApp/src/release/generated/baselineProfiles/ and is committed.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.maxrave.simpmusic.baselineprofile"
    compileSdk = 37

    defaultConfig {
        // BaselineProfileRule collects profiles without root from API 33; Macrobenchmark needs 28+.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Package id of :androidApp's nonMinifiedRelease / benchmarkRelease (set in its build file).
        testInstrumentationRunnerArguments["targetAppId"] = "com.maxrave.simpmusic.bp"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    targetProjectPath = ":androidApp"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
