# Vendored from termux/termux-app — local changes

Source: `termux/termux-app` `terminal-emulator` module (`com.termux.terminal`), upstream version
~0.118.0. Vendored (not a JitPack dependency) so we can adapt the session I/O seam and avoid the
JNI/NDK local-pty build under AGP 9.

## Removed
- **`JNI.java`** — JNI bindings for the local pseudo-terminal (`createSubprocess`, `setPtyWindowSize`,
  `waitFor`, `close`). Not vendored.
- **`src/main/jni/` (`termux.c`, `Android.mk`)** — the native pty. Not vendored. The module build has
  **no `externalNativeBuild`/NDK**.

## Rewritten
- **`TerminalSession.java`** — was a *local process* over a JNI pty. Rewritten to be **backed by an
  external byte-stream pair** (an SSH shell channel):
  - Constructor now takes `(InputStream remoteStdout, OutputStream remoteStdin, Integer transcriptRows,
    TerminalSessionClient client, PtyResizeHandler resizeHandler)` instead of a shell path/args/env.
  - `initializeEmulator()` creates the emulator and starts a reader thread (remote stdout ->
    `mProcessToTerminalIOQueue` -> emulator.append on the main thread) and a writer thread
    (`mTerminalToProcessIOQueue` -> remote stdin). No process spawn, no `waitFor`.
  - `write()` forwards user input to the remote stdin queue (unchanged queue design).
  - `updateSize()` delegates to the new **`PtyResizeHandler`** (the SSH layer calls
    `Session.changeWindowDimensions`) and resizes the emulator.
  - `finishIfRunning()` closes the streams instead of `SIGKILL`. Removed pty-only methods
    (`getCwd`, `getPid`, `wrapFileDescriptor`, `cleanupResources`).

## Build file
- `build.gradle` -> `build.gradle.kts`: AGP 9 (`com.android.library`), `compileSdk = 37`,
  `minSdk = 26`, Java 17, `namespace = "com.termux.terminal"`. Removed `maven-publish`, NDK, ABI
  filters. Dependency: `androidx.annotation` only.

All other classes (`TerminalEmulator`, `TerminalBuffer`, `TerminalRow`, `TerminalColors`,
`KeyHandler`, `WcWidth`, `TextStyle`, `TerminalOutput`, `TerminalSessionClient`, `ByteQueue`,
`Logger`) are **unmodified** from upstream — re-pull them directly when updating.
