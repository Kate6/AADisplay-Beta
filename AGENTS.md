# AGENTS.md

## Project Overview

**AADisplay-Beta** is an Xposed/LSPosed module that enables Android Auto (car display) to mirror almost any app using VirtualDisplay. Fork of `Nitsuya/AADisplay`.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires KEY_ANDROID env var)
./gradlew assembleRelease

# Clean
./gradlew clean
```

**Output**: `aa-display/build/outputs/apk/*/aa-display-<version>.apk`

## Project Structure

```
├── build.gradle.kts          # Root: plugins, repositories
├── settings.gradle.kts       # Modules: :aa-display, :lib-stub
├── gradle.properties         # JVM 17 path, AndroidX config
├── aa-display/               # Main module
│   ├── build.gradle.kts      # compileSdk=36, minSdk=31, targetSdk=36
│   ├── proguard-rules.pro    # Only: -keep class io.github.nitsuya.aa.display.** { *; }
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── assets/xposed_init     # Entry: io.github.nitsuya.aa.display.xposed.XposedInit
│   │   ├── aidl/                 # AIDL interfaces (ICoreManager.aidl)
│   │   └── java/io/github/nitsuya/aa/display/
│   │       ├── xposed/           # Xposed hooks entry, manager, hooks/
│   │       ├── service/          # Android services (AaCarService, ShellManagerService)
│   │       ├── ui/               # Activities, fragments, windows
│   │       └── util/             # AADisplayConfig (SharedPreferences sealed class)
│   └── libs/                     # Pre-built JARs/AARs (do not modify)
│       ├── de.robv.android.xposed_api_82.jar
│       └── aauto.aar
└── lib-stub/                    # Stub implementations for hidden APIs
    └── src/main/java/android/   # System API stubs
```

## Architecture

### Xposed Hook Flow

1. `XposedInit` (entry) → `initZygote()` + `handleLoadPackage()`
2. Package routing:
   - `android` (system) → `AndroidHook`
   - `com.google.android.projection.gearhead` (Android Auto) → `AndroidAuoHook`
   - Other → `OtherHook`
3. `AndroidAuoHook` loads DEX classes via **DexKit** then runs AaHooks:
   - `AaBasicsHook`, `AaSignatureHook`, `AaDpiHook`, `AaBtnEventHook`, `AaUiHook`, `AaPropsHook`

### IPC

Uses **AIDL** (`ICoreManager.aidl`) via `ServiceManager` with package name "package" to communicate with `BridgeService`.

### Hook Base Pattern

```kotlin
abstract class BaseHook {
    var isInit: Boolean = false
    abstract val tagName: String
    abstract fun init(lpparam: XC_LoadPackage.LoadPackageParam)
}
```

## Key Dependencies & Libraries

| Library | Purpose |
|---------|---------|
| `DexKit` 2.0.0-rc3 | Runtime method resolution |
| `EzXHelper` 1.0.3 | Xposed utilities, logging |
| `libsu` 5.2.0 | Root shell command execution |
| `HiddenApiCompat` 4.4.0 | Access hidden Android APIs |
| `Rikkax/refine` 4.4.0 | Annotation processing for hidden APIs |
| `ViewBindingUtil` | ViewBinding utilities |
| `kotlinx-coroutines` | Async operations |

## Configuration Pattern

All settings via `AADisplayConfig` sealed class using `SharedPreferences`:

```kotlin
object LauncherPackage: StringConfig("LauncherPackage", "com.autonavi.amapauto")
object AutoOpen: BooleanConfig("AutoOpen", true)
object VirtualDisplayDpi: IntConfig("VirtualDisplayDpi", 0)
// etc.
```

## Important Gotchas

1. **Version code 3000**: Intentionally high to prevent LSPosed update prompts
2. **XSharedPreferences must be readable**: Check `configPreferences.file.canRead()` before use
3. **DexKit initialization order**: Must call `EzXHelperInit.initAppContext()` then `System.loadLibrary("dexkit")` BEFORE `DexKitBridge.create()`
4. **Package ID 0x64**: Custom package ID configured in `androidResources.additionalParameters`
5. **AppCompat excluded**: `configurations.all { exclude("androidx.appcompat", "appcompat") }` - use Rikkax replacements instead
6. **Pre-built libs**: `libs/` contains JARs/AARs not in any Maven repo - do not delete or replace
7. **ProGuard**: Minimal rules, only keeps module classes

## Code Conventions

- **Logging**: `io.github.nitsuya.aa.display.xposed.log` extension function
- **Hook timing**: `hookBefore` on `Instrumentation.callApplicationOnCreate` for Android Auto
- **Process names**: Android Auto uses multiple processes (`main`, `projection`, `car`)
- **Shell commands**: Via `ShellManagerService` (optional root)

## Head Unit Compatibility Hack (AaHeadUnitHackHook)

New hook to bypass head unit restrictions for third-party apps. Targets:

- **Database cursor spoofing**: `HeadUnitMakeCursorWrapper` intercepts SQLite cursors for `allowedcars` table and replaces `headUnitMake` column values (e.g., "Mobis" → "Google")
- **ContentResolver queries**: Hooks queries for car service data to wrap cursors
- **SharedPreferences**: Forces `allow_unknown_sources=true`, `device_supported=true`
- **Phenotype flags**: Injects compatibility flags

Config options (in `AADisplayConfig`):
- `EnableHeadUnitHack` - Master toggle (default: false)
- `HeadUnitMake` - Actual head unit make to replace (default: "Mobis")
- `ForceAllowUnknownSources` - Allow non-Play Store apps (default: true)
- `BypassValidation` - Skip app validation (default: true)

**How it works**: Kia Soul reports `hu_make="Mobis"` which causes AA to filter out third-party apps. By replacing this value with "Google" (which Head Unit Revived uses) in the database cursors, apps become visible.
