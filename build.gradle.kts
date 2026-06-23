// Top-level build file. Plugins are declared here (apply false) and applied per-module.
plugins {
    // No org.jetbrains.kotlin.android: AGP 9 has built-in Kotlin and forbids applying it.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
