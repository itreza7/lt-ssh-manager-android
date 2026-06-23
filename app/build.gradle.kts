import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Release signing credentials live in keystore.properties (gitignored) — never in version control.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

plugins {
    // Kotlin compilation is provided by AGP's built-in Kotlin support (AGP 9+),
    // so org.jetbrains.kotlin.android is intentionally NOT applied.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.larateam.sshmanager"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.larateam.sshmanager"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "0.1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Sign with the release config when credentials are present (gitignored keystore.properties).
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// Export Room schemas to a version-controlled directory so migrations can be authored
// and validated (MigrationTestHelper). The Room Gradle plugin also wires these JSONs
// into the androidTest assets automatically.
room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<Test>().configureEach {
    testLogging { showStandardStreams = true }
}

dependencies {
    // Core / lifecycle / activity
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose (BOM keeps all Compose artifacts on one compatible version)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    // Pin kotlinx-serialization app-wide (see libs.versions.toml note) so Room's
    // MigrationTestHelper schema deserialization gets a compatible runtime.
    implementation(libs.kotlinx.serialization.json)

    // Secrets: biometric gate (pulls androidx.fragment, used by MainActivity)
    implementation(libs.androidx.biometric)

    // Persistence
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Dependency injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // SSH stack — imported ONLY under the ssh/ package (CLAUDE.md §3 hard rule).
    implementation(libs.sshj)
    implementation(libs.bouncycastle.prov)
    implementation(libs.eddsa)

    // Vendored Termux terminal (terminal-view pulls terminal-emulator transitively).
    implementation(project(":terminal-view"))

    // JVM unit tests (TOFU verifier, backoff/classifier, hermetic MINA SSHD integration)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mina.sshd.core)
    testImplementation(libs.mina.sshd.sftp)
    testImplementation(libs.slf4j.simple)

    // Instrumented tests (Room DAO + repository on an in-memory DB)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
