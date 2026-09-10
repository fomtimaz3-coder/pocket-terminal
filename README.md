# Pocket Terminal

Pocket Terminal is an Android app that starts a real interactive shell inside
the app's private storage. It is not a command-output simulator.

## How it works

- The preferred backend is a small C++ JNI bridge around `forkpty(3)`.
  It launches Android's `/system/bin/sh` with a PTY, so pipes, redirection,
  variables, background jobs, signals, and exit codes are handled by the
  operating system shell.
- If the native PTY cannot be loaded on a device, the app falls back to a
  real `ProcessBuilder("/system/bin/sh", "-i")` session. The fallback keeps
  command execution real but has fewer terminal signal semantics.
- Each tab receives its own private home directory under the app sandbox.
- The emulator consumes streamed UTF-8 bytes and parses common CSI/SGR ANSI
  sequences without blocking the shell reader.
- Android's Storage Access Framework is used only when the user chooses a
  folder. No broad storage permission is requested. The selected tree grant is
  persisted; the shell remains sandboxed, because a `content://` tree is not
  a POSIX directory that can be safely mounted into `/system/bin/sh`.

## Project layout

```text
app/src/main/java/com/pocketterminal/
├── MainActivity.kt
├── core/
│   ├── NativePtyBridge.kt
│   ├── StorageAccess.kt
│   ├── TerminalEmulator.kt
│   ├── TerminalSession.kt
│   └── TerminalSessionManager.kt
└── ui/
    ├── TerminalKeyboard.kt
    ├── TerminalScreen.kt
    └── TerminalTheme.kt
```

## Build

Open the project in Android Studio with Android SDK 35, NDK, and CMake
3.22.1 installed, then run:

```bash
gradle :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Validation checklist

Run these in the app shell:

```sh
pwd
ls
mkdir test
cd test
echo "hello" > file.txt
cat file.txt
cd ..
rm -rf test
echo "hello world" | grep hello
echo "one" && echo "two"
export TEST=123
echo $TEST
```

The command input supports local history and path/command completion. The
auxiliary keyboard sends real escape sequences, Tab, Ctrl+C, and Ctrl+L to
the active shell when appropriate. The UI also supports ANSI colors, text
selection, scrolling, multiple sessions, font-size persistence, and light or
dark chrome.

## Android limitations

This app intentionally does not request root access, bypass scoped storage, or
claim that `/system/bin/sh` is a full desktop Linux distribution. Availability
of utilities such as `bash`, `ps`, or package managers depends on the Android
version and device image. Commands run with the app's normal Android UID and
permissions.