// Vendored from termux/termux-app terminal-emulator (pure-Java VT emulator), adapted to AGP 9 /
// compileSdk 37. The JNI local-pty (JNI.java + jni/termux.c) is intentionally NOT vendored — this
// app drives the emulator from an SSH channel, so TerminalSession is rewritten stream-backed.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.termux.terminal"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.annotation)
}
