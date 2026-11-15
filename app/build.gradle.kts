import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

/** The release version, MAJOR.MINOR.PATCH (see versionCode below). */
val APP_VERSION = listOf(1, 0, 0)
val productName = "Reality Video Editor"

/**
 * Release signing (release round R9), read from `keystore.properties` beside settings.gradle.kts: storeFile (relative to
 * the project folder), storePassword, keyAlias, keyPassword. The file and the keystore stay with their owner — never in
 * the project's history; `keystore.properties.example` shows the form. Without the file the release build is unsigned.
 */
val releaseSigning: Properties? = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }.also { props ->
        val missing = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").filter { props.getProperty(it).isNullOrBlank() }
        require(missing.isEmpty()) { "keystore.properties is missing ${missing.joinToString()}" }
        require(rootProject.file(props.getProperty("storeFile")).isFile) { "keystore.properties: no keystore at ${props.getProperty("storeFile")}" }
    }
}

android {
    namespace = "com.example.prismstudio.p40b43a88"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.example.prismstudio.p40b43a88"
        resValue("string", "app_name", productName)
        buildConfigField("String", "PRODUCT_NAME", "\"$productName\"")
        minSdk = 35
        targetSdk = 37
        // Release versions (round R3): MAJOR.MINOR.PATCH, versionCode = MAJOR·10000 + MINOR·100 + PATCH, so every
        // release's code is higher than the last (1.0.0 → 10000, 1.0.1 → 10001, 1.2.0 → 10200).
        versionCode = APP_VERSION.let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }
        versionName = APP_VERSION.joinToString(".")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters.add("arm64-v8a") }
    }

    signingConfigs {
        releaseSigning?.let { props ->
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 (release round R2): the unshrunk build carried 37 MB of code.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Release testing with the debug automation: `-Pr8` shrinks the debug build as R8 shrinks the release one, so the
        // emulator scripts can drive shrunk code through every page.
        getByName("debug") {
            if (project.hasProperty("r8")) {
                isMinifyEnabled = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            }
        }
        // Release testing: the release build as shipped, installable beside the development build (its own package and
        // data, so a fresh install is a first run) and signed with the debug key. Never uploaded anywhere.
        create("qa") {
            initWith(getByName("release"))
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        resValues = true
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // ViewModels touch android.graphics types only as opaque values in JVM tests.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.spatial.bom))
    implementation(libs.spatial.core)
    implementation(libs.spatial.ui.platform)
    implementation(libs.spatial.ui.foundation)
    implementation(libs.spatial.ui.design)
    implementation(libs.spatial.ui.sense)
    implementation(libs.spatial.ui.tracking)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.media3.common)
    implementation(libs.media3.inspector)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    // Saving the open project when the app goes to the background (already in the graph through the SDK).
    implementation(libs.lifecycle.process)
    // The system document picker (rememberLauncherForActivityResult); already resolved transitively at this version.
    implementation(libs.androidx.activity.compose)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // Compose's tooling (layout inspector, previews) in debug builds only (release round R23): in a release build it brought
    // androidx.compose.ui.tooling.PreviewActivity, exported, which any app could start to run a composable by name.
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.tooling.preview)
}

configurations.all {
    resolutionStrategy {
        exclude("androidx.compose.ui", "ui")
        exclude("androidx.compose.ui", "ui-graphics")
        exclude("androidx.compose.ui", "ui-text")
        exclude("androidx.compose.foundation", "foundation")
    }
}
