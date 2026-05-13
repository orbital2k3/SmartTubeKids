# SmartTube – Build Guide

## Requirements

| Requirement | Version / Notes |
|---|---|
| **JDK** | OpenJDK **14 or older** — newer JDKs cause runtime crashes |
| **Android SDK** | API 34 (compileSdkVersion) |
| **Android NDK** | 21.0.6113669 (set in `smarttubetv/build.gradle`) |
| **Gradle** | Wrapper included (`./gradlew`) — no separate install needed |
| **ADB** | Required only for direct device install targets |

> ⚠️ **JDK version is critical.** JDK 15+ will produce an app that crashes at runtime. Use `java -version` to confirm before building.

---

## 1. Clone & Initialize Submodules

```bash
git clone https://github.com/yuliskov/SmartTube.git
cd SmartTube
git submodule update --init
```

The project has three git submodules:
- `MediaServiceCore` – YouTube API client
- `SharedModules` – shared utilities
- `exoplayer-amzn-2.10.6` – Amazon ExoPlayer fork

---

## 2. Available Flavors

| Flavor | Application ID | Description |
|---|---|---|
| `ststable` | `org.smarttube.stable` | Public stable release |
| `stbeta` | `org.smarttube.beta` | Beta / testing channel |
| `stfdroid` | `app.smarttube.fdroid` | F-Droid build (min SDK 21, no proprietary deps) |

Build task names follow the pattern: `[clean] assemble<Flavor><BuildType>` or `install<Flavor><BuildType>`.

---

## 3. Build Debug APK

### Stable flavor (recommended for development)
```bash
./gradlew assembleStstableDebug
```

### Beta flavor
```bash
./gradlew assembleStbetaDebug
```

### F-Droid flavor
```bash
./gradlew assembleStfdroidDebug
```

### All flavors at once
```bash
./gradlew assembleDebug
```

---

## 4. Build Release APK

> A keystore is required for release builds. See [Signing](#6-signing-release-builds) below.

### Stable flavor
```bash
./gradlew assembleStstableRelease
```

### Beta flavor
```bash
./gradlew assembleStbetaRelease
```

### F-Droid flavor
```bash
./gradlew assembleStfdroidRelease
```

### All flavors at once
```bash
./gradlew assembleRelease
```

---

## 5. Output APK Location & Naming

APKs are written to:
```
smarttubetv/build/outputs/apk/<flavor>/<buildType>/
```

The file name format is:
```
SmartTube_<flavor>_<versionName>_<abi>.apk
```

For example:
```
SmartTube_stable_31.64_arm64-v8a.apk
SmartTube_stable_31.64_armeabi-v7a.apk
SmartTube_stable_31.64_x86.apk
SmartTube_stable_31.64_universal.apk   ← contains all ABIs
```

The `universal` APK is always generated in addition to the per-ABI splits.

---

## 6. Signing Release Builds

Create a `keystore.properties` file in the **project root** (never commit this file):

```properties
storeFile=/path/to/your/keystore.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

If this file is absent, the release build will still compile but will be unsigned (not installable on a device without manual signing).

To generate a new keystore:
```bash
keytool -genkey -v -keystore my-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias my-alias
```

---

## 7. Build & Install Directly to a Device

### Over ADB (USB or network)
```bash
# Connect via network (optional, skip for USB)
adb connect <device_ip_address>

# Build and install stable debug
./gradlew installStstableDebug

# Build and install beta debug
./gradlew installStbetaDebug
```

### Full clean before install
```bash
./gradlew clean installStstableDebug
```

---

## 8. Clean Build

```bash
./gradlew clean
```

Or combined with a build task:
```bash
./gradlew clean assembleStstableDebug
```

---

## 9. Useful Individual Tasks

| Task | Purpose |
|---|---|
| `./gradlew tasks` | List all available tasks |
| `./gradlew dependencies` | Print the full dependency tree |
| `./gradlew lint` | Run lint checks (aborts on errors by default) |
| `./gradlew test` | Run unit tests |
| `./gradlew :smarttubetv:dependencies` | Dependency tree for the app module only |

---

## 10. Gradle Memory

If the build runs out of memory, increase the JVM heap in `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4096m
```

The project ships with `-Xmx3000m` by default.

---

## 11. Build from Android Studio

1. Open the project root in **Android Studio**.
2. Let Gradle sync complete.
3. Select the desired build variant from **Build → Select Build Variant** (e.g., `ststableDebug`).
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)** to generate the APK, or use the Run button to build and install directly to a connected device/emulator.
