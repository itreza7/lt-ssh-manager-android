# Vendored from termux/termux-app — local changes

Source: `termux/termux-app` `terminal-view` module (`com.termux.view`), upstream version ~0.118.0.

## Java sources
- **Unmodified** from upstream (`TerminalView`, `TerminalRenderer`, `TerminalViewClient`,
  `GestureAndScaleRecognizer`, `textselection/*`, `support/*`). `TerminalView` still works against
  `com.termux.terminal.TerminalSession` — which our terminal-emulator module rewrote to be
  stream-backed, so no change is needed here. Re-pull directly when updating.

## Resources
- `src/main/res/` (the text-selection handle drawables + `strings.xml`) copied unmodified.

## Build file
- `build.gradle` -> `build.gradle.kts`: AGP 9 (`com.android.library`), `compileSdk = 37`,
  `minSdk = 26`, Java 17, `namespace = "com.termux.view"`. Removed `maven-publish`. Dependencies:
  `api(project(":terminal-emulator"))` + `androidx.annotation`.
