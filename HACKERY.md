# AADisplay's Android Auto Hackery

A technical summary of the inventive tricks this module uses to intercept and control Android Auto.

---

## 1. Signature Verification Bypass (`AaSignatureHook`)

Uses **DexKit** to find the APK signature checker class by searching for the string `"Package has more than one signature."` and a method with signature `public final boolean(String)`. Then hooks the method to return `true` when called for the module's own package name, letting AA think it's a legitimate automotive app.

---

## 2. Fake Installer (`AaBasicsHook`)

Hooks `InstallSourceInfo.getInitiatingPackageName()` (or `PackageManager.getInstallerPackageName()` on older APIs) to return `"com.android.vending"` (Play Store), hiding that the app was installed via LSPosed/Xposed.

---

## 3. Phenotype Property Injection (`AaPropsHook`)

This is one of the cleverest hacks:

- Finds Google's hidden `ConfigurationController` class via DexKit string matching (`"Must call PhenotypeContext.setContext() first"`)
- Hooks the config lookup method to inject custom `com.google.android.projection.gearhead` properties, with type coercion (String → Boolean, Int, Long, or complex object properties)
- Also hooks `ContentResolver.query()` for `com.google.android.gms.car` to merge additional properties via `MatrixCursor` + `MergeCursor`, allowing arbitrary Phenotype config overrides

---

## 4. DPI Override (`AaDpiHook`)

Uses DexKit to find `CarDisplay` and `DisplayParams` constructors by searching for the string `"DisplayParams(selectedIndex="`. Intercepts constructor calls to inject custom `densityDpi` values into the display parameters.

---

## 5. UI Layout Injection (`AaUiHook`)

The most visible hack:

- Finds `LayoutInfo` constructor via DexKit and replaces layout resources (LHD/RHD canonical rail layouts)
- Intercepts `LayoutInflater.inflate()` for the facet bar to completely replace AA's navigation bar with a custom one
- **Injects custom buttons** (Home, Fullscreen, Back) that send broadcast intents to the module's service
- **Hides/disables native launcher buttons** by making them perform long-click instead of normal click
- **Auto-launches** the virtual display on first load by calling `CarSystemUiControllerService.a()` via reflection
- **Hooks `View.setOnClickListener`** with a custom `FinallyListener` interface to allow multiple click listeners (AA normally restricts views to single listeners)

---

## 6. Steering Wheel Key Interception (`AaBtnEventHook`)

Hooks `ContextWrapper.registerReceiver()` to capture `MEDIA_BUTTON` and `KEY_EVENT` broadcasts **before** AA can consume them. Aborts the original delivery and re-broadcasts intercepted keys as steering wheel commands (`ACTION_STEERING_WHEEL_CONTROL`) to the module's service, properly handling long-press vs. short-press detection.

---

## 7. Corner Radius Removal (`AaUiHook`)

Hooks `ProjectionWindowDecorationParams` constructor to force `cornerRadius = 0`, giving the projection window sharp edges instead of rounded corners.

---

## 8. System Service IPC (`BridgeService` + `CoreManager`)

A sophisticated binder-based IPC system:

- Hooks `ServiceManager.addService("package")` to intercept the `IPackageManager` service
- Injects a custom handler into `IPackageManager.onTransact()` with a magic transaction code (`'AADD'`)
- Clients communicate with the module via AIDL-generated `ICoreManager` interface
- Allows remote control of display creation, surface management, task movement, touch input, and key injection

---

## 9. Display Permission Escalation (`AndroidHook`)

Hooks `isCallerAllowedToLaunchOnDisplay()` to return `true` for the module when launching on AA's virtual display, bypassing Android's normal display launch restrictions.

---

## 10. Custom Density for Apps (`AndroidHook.FuckAppUseApplicationContext`)

Tracks which apps start on which display via `ActivityTaskManagerService.startProcessAsync()`, then modifies their `Configuration.densityDpi` during `IApplicationThread.bindApplication()` so apps render at the correct density on the car's display.

---

## 11. CarLink Intent Rewriting (`LauncherHook`)

Intercepts `Instrumentation.execStartActivity()` to rewrite launcher intents for various car link protocols:

- **UCAR**: Rewrites to `com.ucar.intent.action.UCAR` with `isUcarMode=true`
- **Samsung**: Rewrites to `samsung.intent.action.carlink.kit`
- **Vivo**: Rewrites to `vivo.intent.action.carlink.kit` with `isVivoCarLinkMode=true`
- **Huawei**: Special-cases Kugou to launch `com.kugou.android.app.hicar.HiCarSplashActivity` with `isHiCarMode=true`

---

## 12. Status Bar Height Zeroing (`OtherHook`)

Hooks `Resources.getDimension()` (and variants) to return `0` for `status_bar_height`, effectively hiding the system status bar on the virtual display.

---

## 13. VirtualDisplay Management (`AaVirtualDisplayAdapter` + `DisplayWindow`)

Creates and manages the actual `VirtualDisplay` instance, handles:

- Surface creation and buffer management
- Touch input transformation (coordinate scaling from car display to virtual display)
- Window creation with configurable IME policy
- Task tracking and recent apps management
- Power mode control
- Surface mirroring for overlay windows

---

## Key Techniques Used

| Technique | Purpose |
|-----------|---------|
| **DexKit** | Runtime method/class discovery in obfuscated AA DEX |
| **Xposed hooks** | Method interception at various Android layers |
| **AIDL + Binder IPC** | Cross-process communication |
| **Phenotype injection** | Override AA's configuration system |
| **Layout inflation interception** | Replace UI at runtime |
| **Broadcast interposition** | Capture and redirect system events |
| **SurfaceControl mirroring** | Layer apps on the car display |
| **VirtualDisplay API** | Create the projection surface |

---

## 14. Head Unit Compatibility Bypass (`AaHeadUnitHackHook`)

New hook that targets the head unit compatibility filtering system:

- **Phenotype flag injection**: Injects `Watevra__car_hardware_headunit_filter_list`, `CarActivityManager__gmm_start_sub_phase_timers_hu_make_allow_list`, and `AppValidation__*` flags via `ContentResolver.query()` hook on the phenotype authority
- **Unknown sources bypass**: Hooks `SharedPreferences.getBoolean()` to always return `true` for `allow_unknown_sources`
- **Validation bypass**: Hooks validation methods in the `com.google.android.projection.gearhead.validation` package

**Config options**:
- `EnableHeadUnitHack` (default: false) - Master toggle for head unit hacks
- `HeadUnitMake` (default: "Mobis") - Head unit manufacturer to report
- `HeadUnitModel` (default: "standard_m_5") - Head unit model to report
- `ForceAllowUnknownSources` (default: true) - Allow third-party installers
- `BypassValidation` (default: true) - Skip app validation

**Targeted flags**:
```
AppValidation__should_bypass_validation = false  (maintain security)
AppValidation__dhu_bypass_validation = true        (allow non-DHU)
ProjectedAppsFeature__enabled = true               (enable projected apps)
RemoteCarApps__enabled = true                      (enable car-side apps)
Watevra__car_hardware_headunit_filter_list = ""     (no head unit restrictions)
CarActivityManager__gmm_start_sub_phase_timers_hu_make_allow_list = "Mobis,Google,Pioneer,Kenwood,JVC,Sony,Alpine,Clarion,Parrot"
AppValidation__allowed_package_list = ""           (no package restrictions)
AppValidation__blocked_packages_by_installer = ""   (no installer blocks)
```
