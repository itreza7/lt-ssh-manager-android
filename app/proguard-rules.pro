# R8 / ProGuard rules.
#
# Room/Hilt generated code ships its own consumer rules. The SSH stack below is
# reflection / service-loader heavy and must be kept for minified (release) builds.

# --- sshj ---------------------------------------------------------------------
# sshj loads transport components, key types, and factories by reflection/SPI.
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.sshj.** { *; }
-dontwarn net.schmizz.**
-dontwarn com.hierynomus.sshj.**

# --- BouncyCastle -------------------------------------------------------------
# Provider + algorithms are resolved by name; keep the whole provider.
-keep class org.bouncycastle.** { *; }
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-dontwarn org.bouncycastle.**

# --- EdDSA (ed25519) ----------------------------------------------------------
-keep class net.i2p.crypto.eddsa.** { *; }
-dontwarn net.i2p.crypto.eddsa.**

# --- SLF4J (sshj logging facade; no binding bundled) --------------------------
-dontwarn org.slf4j.**

# --- Optional sshj dependencies we don't ship (avoid missing-class errors) ----
-dontwarn com.jcraft.jzlib.**
-dontwarn net.i2p.crypto.eddsa.**
-dontwarn org.newsclub.net.unix.**
-dontwarn com.hierynomus.sshj.signature.**

# --- Vendored Termux terminal modules (Phase 5) -------------------------------
# terminal-emulator/terminal-view reference members across the JNI boundary and via
# the TerminalSessionClient/TerminalViewClient callbacks; keep them whole.
-keep class com.termux.** { *; }
-dontwarn com.termux.**

# --- Room (Phase 1/8/11) ------------------------------------------------------
# Room's generated code references entities/DAOs directly; keep entities to be safe.
-keep class com.larateam.sshmanager.data.db.** { *; }

# --- Persistence/session metadata (Phase 11) ----------------------------------
# Restored via a pure codec + enum valueOf() (enum members are kept by the default
# android-optimize config); keep the model + enums so restore survives minification.
-keep class com.larateam.sshmanager.session.** { *; }
-keep class com.larateam.sshmanager.data.model.** { *; }
-keep class com.larateam.sshmanager.sftp.SftpEntryType { *; }

# --- kotlinx-serialization (defensive) ----------------------------------------
# Runtime is on the classpath; keep serializer lookups working even if @Serializable
# is introduced later. Harmless if unused.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn kotlinx.serialization.**

# --- DataStore (Preferences) --------------------------------------------------
# Preferences DataStore uses no reflection; only suppress optional proto warnings.
-dontwarn androidx.datastore.**
