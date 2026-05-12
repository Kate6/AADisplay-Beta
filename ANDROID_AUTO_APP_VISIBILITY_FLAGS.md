# Controlling App Visibility in the Android Auto Launcher

This document catalogs every mechanism discovered in the Android Auto (Gearhead) codebase for controlling which apps appear in the Android Auto launcher/home screen. This covers both phone-projected apps (3rd-party apps running on the phone and mirrored to the car's display) and car-side (AAOS) apps.

---

## Architecture Overview

Android Auto's app visibility system operates through several layers. Understanding the flow is essential before modifying any flags.

### The App Registration Flow

```
1. App declares CarAppService in manifest
   └─ android:exported="true"
   └─ Intent filter: androidx.car.app.CarAppService
   └─ Category: androidx.car.app.category.* (MEDIA, NAVIGATION, MESSAGING, etc.)

2. App registers with CATEGORY_PROJECTION (phone-projected) or CATEGORY_PROJECTION_OEM (car-side)

3. Gearhead's AppHost queries PackageManager for all matching services

4. Filtering pipeline evaluates each candidate service
   ├─ Feature gate check
   ├─ Package validation (allowlist/blocklist)
   └─ Component enabled state check

5. Passed apps rendered in launcher by ProjectedHomeService + LauncherCarAppService
```

### Key Services Involved

| Service | Class | Role |
|---------|-------|------|
| Launcher home | `ProjectedHomeService` / `jvp.smali` | Renders the main app grid |
| Launcher template | `LauncherCarAppService` | CarAppService wrapping launcher screen |
| App launcher | `GhAppLauncherService` | Hosts third-party car apps |
| OEM All Apps | `AllCarAppsService` | Lists OEM car-side apps |
| OEM Exit | `OEMExitService` | Provides exit/All Apps functionality |
| Validation | `CarProjectionValidator` | Package/component filtering |
| Feature gates | `Acpk.smali`, `Acqd.smali` | Phenotype flag readers |

### Component Listing (meb.smali)

The `Lmeb` class defines all 30+ car services as static `ComponentName` constants. This is the canonical list of services the app knows about. Services are registered in the manifest and referenced here for service resolution.

---

## Layer 1: Master Feature Toggles

These flags gate an entire category of apps on or off.

### `ProjectedAppsFeature__enabled`

- **Type**: Boolean (phenotype)
- **File**: `smali/acpm.smali` (line 22), `smali/dlq.smali` (line 8270)
- **Default**: `false` (would mean no phone-projected apps appear)
- **Purpose**: **Master kill switch** for all phone-projected apps in the launcher. When `false`, the feature gate `Lacpk.b()` returns falsy and no 3rd-party apps pass through.
- **Code pattern**:
  ```smali
  sget-object v0, Lacfq;->b:Lxrm;
  const-string v1, "ProjectedAppsFeature__enabled"
  const/4 v2, 0x0
  invoke-virtual {v0, v1, v2}, Lxrm;->e(Ljava/lang/String;Z)Lvyv;
  sput-object v0, Lacpm;->a:Lvyv;
  ```
- **How to disable**: Nop out the `iget-boolean` check in `ssg.smali` or `kta.smali`, or set the flag to `false` in GMS Prefs.

### `RemoteCarApps__enabled`

- **Type**: Boolean (phenotype)
- **File**: `smali/acqf.smali` (line 22), `smali/dlq.smali` (line 8614)
- **Purpose**: Controls whether apps running on AAOS (car-side, not phone-projected) appear in the launcher. Read via feature gate `Lacqd`.
- **Code pattern**: Same as above, but reads `RemoteCarApps__enabled`.

### `LauncherShortcuts__enabled`

- **Type**: Boolean (phenotype)
- **File**: `smali/dlq.smali` (line 6304), read via `Lacmq;->e()`
- **Purpose**: Enables launcher shortcuts (assistant shortcuts pinned to the launcher grid).
- **Related flag**: `LauncherShortcuts__assistant_shortcut_enabled` — specifically controls assistant shortcuts.

### `All Vehicle Apps Toggle`

The "All vehicle apps" feature (rendered by `OEMExitService`) does **not** appear to have a direct phenotype flag. Its visibility is controlled by:
- Manifest registration of `com.google.android.projection.gearhead.oem.AllCarAppsService`
- The `OEM_FACET` string constant used in UI routing
- Potentially `Watevra__projection_state_api_packages_allowed` (may restrict which packages can use the projection state API)
- The car's AppDecorService manages OEM component visibility

---

## Layer 2: AppValidation Package Filtering

These flags define **who is allowed to project at all**, regardless of feature gates.

### `AppValidation__allowed_package_list`

- **Type**: String (comma-separated package names, TypedFeatures.StringListParam proto)
- **File**: `smali/dlq.smali` (line 706)
- **Purpose**: Explicit **allowlist**. If non-empty, only packages matching names in this list can project. Any package not listed is rejected.
- **Code context**:
  ```smali
  const-string v0, "AppValidation__allowed_package_list"
  invoke-static {}, Lacge;->e()Ljava/lang/String;  # Returns comma-separated pkg names
  move-result-object v1
  invoke-static {p0, v4, v0, v1}, Ldlq;->K(...)  # Dump to output
  ```
- **How it works**: `Lacge` is a flag reader that returns the string list from the proto. An empty or unset value means **no allowlist** is active (all packages pass).
- **Example value**: `"com.spotify.music,com.google.android.youtube"`
- **Impact**: If set, every projected app must be listed here or it will be rejected by `AppValidation`.

### `AppValidation__blocked_packages_by_installer`

- **Type**: String (blocklist keyed by installer, TypedFeatures.StringListParam proto)
- **File**: `smali/dlq.smali` (line 722)
- **Purpose**: **Blocklist by installer package**. Apps installed by specified installer packages are blocked. Structured as a map or list in the proto.
- **Code context**:
  ```smali
  const-string v1, "AppValidation__blocked_packages_by_installer"
  const-string v5, "com.google.protos.experiments.proto.TypedFeatures.StringListParam"
  invoke-static {p0, v5, v1, v0}, Ldlq;->K(...)  # Dump to output
  ```
- **How it works**: `Lacge;->c()` reads the blocked packages. `Lacge` appears to handle both allowlists and blocklists.

### `AppValidation__should_bypass_validation`

- **Type**: Boolean
- **Purpose**: When `true`, **skips all package validation**. Any app passes through regardless of allowlists, blocklists, or other checks. Effectively a master bypass for testing.

### `AppValidation__dhu_bypass_validation`

- **Type**: Boolean
- **Purpose**: Bypass validation specifically for DHU (Desktop Head Unit) testing. Useful for developers testing apps without going through validation.

### `AppValidation__swallow_play_api_exception`

- **Type**: Boolean
- **Purpose**: When `true`, suppresses Play API exceptions during validation instead of propagating them. Validation failures become silent.

### `AppValidation__allowed_3p_installers`

- **Type**: String (TypedFeatures.StringListParam)
- **Purpose**: Explicit list of allowed third-party installers. Apps installed by anything not in this list are blocked.

### `AppValidation__swallow_play_api_exception_return_value`

- **Type**: String (return value when exception is swallowed)
- **Purpose**: Controls what value is returned when Play API exceptions are suppressed.

### Validation Code Pattern (dlq.smali)

The `dlq` class appears to be a developer/debug dump tool that outputs all flag values. The actual validation logic lives in separate classes (`Lacil`, `Lacge`, etc.) that are called by the app's runtime code:

```smali
# Pattern for boolean flag read
invoke-static {}, Lacil;->d()Z    # e.g. CarProjectionValidator__include_enabled_only_components
move-result v0
invoke-static {v0}, Lghx;->ad(Z)Ljava/lang/String;
move-result-object v0
invoke-static {p0, v3, v1, v0}, Ldlq;->K(...)  # Print to output

# Pattern for string flag read
invoke-static {}, Lacge;->e()Ljava/lang/String;  # e.g. AppValidation__allowed_package_list
move-result-object v1
invoke-static {p0, v4, v0, v1}, Ldlq;->K(...)  # Print to output
```

---

## Layer 3: CarProjectionValidator — Component-Level Filtering

These flags control how individual components (activities, services, receivers) within a package are filtered.

### `CarProjectionValidator__include_enabled_only_components`

- **Type**: Boolean
- **File**: `smali/dlq.smali` (line 2968), read via `Lacil;->d()`
- **Purpose**: When `true`, only manifests components explicitly marked `android:enabled="true"` are allowed. Components without the attribute or marked `android:enabled="false"` are filtered out.
- **Default behavior**: Disabled means components without an explicit `enabled` attribute are included by default.

### `CarProjectionValidator__filter_disabled_packages_in_ispackageallowed_method`

- **Type**: Boolean
- **File**: `smali/dlq.smali` (line 2980), read via `Lacil;->c()`
- **Purpose**: When `true`, the `isPackageAllowed()` method additionally filters out packages that are disabled via `PackageManager`. This checks the package's enabled state as reported by the system.
- **Use case**: Prevents disabled apps from appearing even if they're otherwise allowed.

### `CarProjectionValidator__log_reason_apps_not_allowed_all_apps`

- **Type**: Boolean
- **File**: `smali/dlq.smali` (line 2992), read via `Lacil;->e()`
- **Purpose**: When `true`, logs detailed reasons for why each app is not allowed into the `AllApps` debug/log output. Useful for debugging why specific apps don't appear.

### `CarProjectionValidator__measure_latency_enabled`

- **Type**: Boolean
- **File**: `smali/dlq.smali` (line 3004), read via `Lacil;->f()`
- **Purpose**: Enables latency measurement for the validation pipeline. Logs how long each validation step takes.

### `CarAppLibrary__cluster_force_disable_package_list`

- **Type**: String (TypedFeatures.StringListParam)
- **Purpose**: Comma-separated list of package names that are **forcibly disabled** from appearing in the **instrument cluster** template. Apps in this list can still appear on the main head unit but not in cluster displays.

---

## Layer 4: Category-Specific Allowlists and Blocklists

These flags restrict specific categories of apps.

### Navigation Apps

| Flag | Type | Purpose |
|------|------|---------|
| `ControlSupportedNavApps__allowlisted_nav_packages` | String (allowlist) | Only packages in this list are shown as supported navigation apps |
| `ControlSupportedNavApps__denylisted_nav_packages` | String (denylist) | Packages in this list are excluded from navigation apps |

The **allowlist takes precedence** — if set, only listed packages are shown. The denylist removes specific packages from that set.

### Cradle / Launcher

| Flag | Type | Purpose |
|------|------|---------|
| `CradleFeature__app_launcher_package_list` | String | Restricts which packages appear in the cradle/launcher UI |
| `CradleFeature__app_package_list` | String | Package list for cradle feature |
| `CradleFeature__allowed_activities_list` | String | Only these specific activities are allowed for cradle |

### Other Category Filters

| Flag | Category | Purpose |
|------|----------|---------|
| `Media__show_settings_button_in_browse_view_component_denylist` | Media | Denylist for settings button in browse view |
| `Messaging__app_block_list` | Messaging | Blocklist of messaging apps |
| `MessagingNotificationFilter__package_list` | Messaging | Notification filtering by package |
| `MessagingAvatarCircularCrop__package_list` | Messaging | Per-package avatar circular crop settings |
| `NotificationDebugging__package_list` | Debug | Filter notification debugging by package |
| `WirelessMonitorMode__package_allow_list_proto` | Wireless | Allowlist for wireless monitor mode (protobuf format) |

---

## Layer 5: Per-Feature Display Toggles

These flags control UI elements within individual app categories.

| Flag | Category | Purpose |
|------|----------|---------|
| `HeadUnitFeature__show_update_notifications_enabled` | Update notifications | Show/hide update notifications on head unit |
| `HeroFeature__show_weather_by_default_on_portrait_kill_switch` | Weather | Show weather by default on portrait |
| `CradleFeature__show_dpi_picker` | Display | Show DPI picker in cradle settings |
| `ProjectionWindowManager__show_display_information` | Display | Show display information in projection window |
| `Watevra__hide_minimized_state_template_view_when_on_stop_called` | Templates | Hide template view when `onStop()` is called |
| `LifecycleRecovery__show_toast_on_usb_reset` | Recovery | Show toast on USB reset |
| `Media__display_disable_autoplay_notification_enabled` | Media | Disable autoplay notification |

---

## Layer 6: SharedPreferences / User-Configurable Settings

These are **runtime toggleable** by the user through the in-car settings UI or developer settings. They persist across sessions.

### From `res/xml/car_preferences.xml` and `res/xml/developer_preferences.xml`

| Preference Key | Type | Location | Purpose |
|---------------|------|----------|---------|
| `allow_unknown_sources` | CheckBox | Developer settings | Allows installation of apps from unknown sources (non-Play Store). When disabled, only Play Store apps can be installed and shown. |
| `car_app_mode` | ListPreference | Both | App mode selection (day/night, etc.) |
| `failure_injection_enabled` | CheckBox | Developer settings | Enable chaos engineering — injects random failures |
| `debug_overlay_enabled` | CheckBox | Developer settings | Show debug overlay |
| `input_config_enabled` | CheckBox | Developer settings | Enable input configuration |
| `car_gal_snoop_options` | PreferenceScreen | Both | GAL logging submenu |
| `car_gal_snoop_buffer_size` | EditText | Both | Size of GAL snoop buffer |
| `touchpad_tuning` | PreferenceScreen | Both | Touchpad tuning submenu |
| `touchpad_tuning_enabled` | Switch | Both | Master toggle for touchpad tuning |
| `touchpad_base_fraction` | SeekBar | Both | Touchpad base fraction (1-10) |
| `touchpad_min_size_mm` | SeekBar | Both | Touchpad minimum size in mm (0-15) |
| `touchpad_multimove_penalty_mm` | SeekBar | Both | Touchpad multimove penalty in mm (0-15) |

### From App Code (SharedPreferences)

| Preference Key | Type | Purpose |
|---------------|------|---------|
| `car_force_logging` | Boolean | Force debug logging regardless of other settings |
| `car_collect_gps_data` | Boolean | Collect GPS data |
| `car_disable_anr_monitoring` | Boolean | Disable ANR (Application Not Responding) monitoring |
| `car_enable_audio_latency_dump` | Boolean | Dump audio latency data |
| `car_enable_gal_snoop` | Boolean | Enable GAL (Generic Abstraction Layer) snoop logging |
| `car_save_video / _audio / _mic` | Boolean | Capture media streams for debugging |
| `car_dump_screenshot` | Boolean | Dump screenshots on capture |
| `car_video_resolution` | List | Video resolution for capture |
| `car_audio_codec` | List | Audio codec for capture |
| `car_handoff_is_first_connection` | Boolean | First-time connection flag |
| `car_handoff_is_car_audio_service_migration_enabled` | Boolean | Audio service migration toggle |
| `car_only_connect_to_known_cars` | Boolean | Only connect to previously paired cars |
| `car_ev_settings_enabled` | Boolean | Show EV (Electric Vehicle) settings |
| `car_ev_features_enabled` | Boolean | Enable EV features |
| `aa_google_setting_enabled` | Boolean | AA Google setting toggle |
| `google_location_accuracy_enabled` | Boolean | High-accuracy location |
| `location_enabled` | Boolean | Location feature toggle |
| `toll_card_sensor_enabled` | Boolean | Toll card sensor feature |
| `developer_settings_enabled` | Boolean | Developer mode enabled |
| `device_supported` | Boolean | Device support check result |
| `device_check_completed` | Boolean | Device check has been run |
| `device_policy` | String | Device policy string |
| `accessibility_display_magnification_enabled` | Boolean | Display magnification for accessibility |
| `high_text_contrast_enabled` | Boolean | High text contrast mode |
| `client_side_throttling_supported` | Boolean | Client-side throttling |
| `alpha_jump_language_supported` | Boolean | Alpha jump language support |
| `touchpad_nav_enabled` | Boolean | Touchpad navigation |
| `adb_enabled` | Boolean | ADB debugging |
| `bugreport_multi_display_screenshot_enabled` | Boolean | Screenshot all displays |
| `optimized_car_activity_enabled` | Boolean | Optimized car activity |
| `optimized_car_activity_package_names` | String | Package list for optimized car activity |
| `auto_configured_internal` | Boolean | Internal auto-configuration flag |
| `key_settings_messaging_notifications_enabled` | Boolean | Key-based settings for messaging notifications |
| `key_settings_messaging_visual_preview_enabled` | Boolean | Visual preview in messaging |
| `key_settings_notification_chime_enabled` | Boolean | Notification chime |
| `weather_config` | SharedPrefs | Weather configuration |
| `car_day_night_mode` | String | Day/night mode |

---

## Layer 7: Hardcoded Boolean Resources

These are compiled into `res/values/bools.xml` and cannot be changed at runtime without recompiling the APK.

| Resource | Value | Purpose |
|----------|-------|---------|
| `gearhead_sdk_supports_window_insets` | `true` | Window insets support |
| `m3_focus_rings_enabled` | `false` | Material 3 focus rings (disabled by default) |
| `settings_work_profile_support_default` | `false` | Work profile support (disabled by default) |
| `settings_autolaunch_enable_default` | `true` | Auto-launch enabled by default |
| `settings_notification_chime_enable_default` | `true` | Notification chime enabled by default |
| `settings_gemini_barge_in_enabled` | `true` | Gemini barge-in (enabled) |
| `settings_gemini_barge_in_enabled_fail_safe` | `false` | Fail-safe for Gemini barge-in (disabled) |

---

## Layer 8: AssistantFeatureFlags (gRPC from Assistant Service)

These flags come from the Assistant service via gRPC (`GetAssistantFeatureFlags`), not from the GMS Phenotype database. They are stored in the `Lolb` class (`smali/olb.smali`).

| Flag Field | Flag Name | Affects App Visibility? |
|-----------|-----------|------------------------|
| `Lolb.c` | `coolwalkAssistantSuggestionsEnabled` | Suggestions in Coolwalk (not app listing) |
| `Lolb.d` | `feedbackInitializationIntentEnabled` | Feedback initialization |
| `Lolb.e` | `dodgeboostEnabled` | Quick handshake (not app listing) |
| `Lolb.f` | `coolwalk_direct_action_enabled` | Direct actions (not app listing) |
| `Lolb.g` | `coolwalk_streamed_media_recommendations_enabled` | Media recs (not app listing) |
| `Lolb.h` | `media_recommendation_callbacks_service_enabled` | Media callbacks (not app listing) |
| `Lolb.i` | `notification_proto_enabled` | Notification protocol (not app listing) |
| `Lolb.j` | `send_message_to_contact_using_provider_client_input_enabled` | Messaging (not app listing) |
| `Lolb.k` | `gearhead_smart_action_enabled` | Smart actions (not app listing) |
| `Lolb.l` | `kitt_enabled` | KITT voice assistant (not app listing) |
| `Lolb.n` | `kitt_eligible` | KITT eligibility (not app listing) |
| **`Lolb.o`** | **`car_control_enabled`** | **Car controls feature — may affect car control app visibility** |
| `Lolb.b` | *(version int)* | Protocol version |

**None of the AssistantFeatureFlags directly control whether an app shows up in the launcher.** They control feature availability within apps that are already visible. The `car_control_enabled` flag may affect the Car Control app's visibility since it's sent to the Assistant as part of the `AssistantFeatureFlags` payload.

### How Lolb Flags Are Read

```smali
# In kih.smali (~line 192-468):
sget-object p1, Lolb;->a:Lolb;          # Get singleton instance
iget-boolean v1, p1, Lolb;->c:Z           # Read specific flag field
invoke-virtual {v0, v2, v1}, Lrte;->a(...);  # Log flag value
```

### Lolb Flag Usages Across Codebase

| File | Field | Purpose |
|------|-------|---------|
| `ktc.smali:2372` | `Lolb;->n` | KITT eligible check |
| `ktc.smali:3985` | `Lolb;->n` | KITT check |
| `ktc.smali:4227` | `Lolb;->n` | KITT check |
| `ktc.smali:2471` | `Lolb;->j` | Send message flag |
| `ktc.smali:6288` | `Lolb;->c` | Coolwalk suggestions |
| `ktc.smali:6341` | `Lolb;->c` | Coolwalk suggestions |
| `ssg.smali:870` | `Lolb;->e` | Dodgeboost |
| `ssg.smali:1064` | `Lolb;->a` | Get singleton |
| `ssg.smali:1607` | `Lolb;->k` | Smart actions |
| `ssg.smali:2142` | `Lolb;->h` | Media rec |
| `ssg.smali:5086` | `Lolb;->i` | Notification proto |
| `ssg.smalami:6556` | `Lolb;->h` | Media rec |
| `kta.smali:140` | `Lolb;->l` | KITT |
| `kta.smali:202` | `Lolb;->m` | Field m |
| `kta.smali:973` | `Lolb;->l` | KITT |
| `kta.smali:1901` | `Lolb;->c` | Coolwalk suggestions |
| `kta.smali:2004` | `Lolb;->n` | KITT eligible |
| `kta.smali:2165` | `Lolb;->n` | KITT eligible |
| `kta.smali:2189` | `Lolb;->l` | KITT |

---

## Complete Flag Reference Table

| Flag | Type | Layer | Affects Launcher |
|------|------|-------|-------------------|
| `ProjectedAppsFeature__enabled` | Bool | 1 | **YES — master toggle for all projected apps** |
| `RemoteCarApps__enabled` | Bool | 1 | **YES — toggles car-side apps** |
| `LauncherShortcuts__enabled` | Bool | 1 | Shows launcher shortcuts |
| `LauncherShortcuts__assistant_shortcut_enabled` | Bool | 1 | Shows assistant shortcuts |
| `AppValidation__allowed_package_list` | String | 2 | **YES — explicit allowlist** |
| `AppValidation__blocked_packages_by_installer` | String | 2 | **YES — blocks by installer** |
| `AppValidation__should_bypass_validation` | Bool | 2 | **YES — bypasses all validation** |
| `AppValidation__dhu_bypass_validation` | Bool | 2 | Bypasses for DHU |
| `AppValidation__allowed_3p_installers` | String | 2 | Installer allowlist |
| `AppValidation__swallow_play_api_exception` | Bool | 2 | Silent failures |
| `CarProjectionValidator__include_enabled_only_components` | Bool | 3 | Filter disabled components |
| `CarProjectionValidator__filter_disabled_packages_in_ispackageallowed_method` | Bool | 3 | Filter PM-disabled packages |
| `CarProjectionValidator__log_reason_apps_not_allowed_all_apps` | Bool | 3 | Debug logging |
| `CarProjectionValidator__measure_latency_enabled` | Bool | 3 | Latency measurement |
| `CarAppLibrary__cluster_force_disable_package_list` | String | 3 | Force-disable for cluster |
| `ControlSupportedNavApps__allowlisted_nav_packages` | String | 4 | Navigation allowlist |
| `ControlSupportedNavApps__denylisted_nav_packages` | String | 4 | Navigation blocklist |
| `CradleFeature__app_launcher_package_list` | String | 4 | Cradle launcher filter |
| `CradleFeature__app_package_list` | String | 4 | Cradle package list |
| `CradleFeature__allowed_activities_list` | String | 4 | Cradle activity filter |
| `CradleFeature__show_dpi_picker` | Bool | 5 | Show DPI picker |
| `Media__show_settings_button_in_browse_view_component_denylist` | String | 4 | Settings button denylist |
| `Messaging__app_block_list` | String | 4 | Messaging blocklist |
| `WirelessMonitorMode__package_allow_list_proto` | Proto | 4 | Wireless allowlist |
| `HeadUnitFeature__show_update_notifications_enabled` | Bool | 5 | Update notifications |
| `allow_unknown_sources` | Bool (prefs) | 6 | Unknown source apps |
| `failure_injection_enabled` | Bool (prefs) | 6 | Chaos engineering |
| `car_control_enabled` | Bool (Lolb) | 8 | May affect car control app |
| `car_ev_settings_enabled` | Bool (prefs) | 6 | EV settings visibility |
| `car_ev_features_enabled` | Bool (prefs) | 6 | EV features |
| `Watevra__projection_state_api_packages_allowed` | String | 2 | Projection state API access |

---

## How to Disable an App from Appearing

### Method 1: Phenotype Allowlist (Most Effective)

Add the app's package name to `AppValidation__allowed_package_list` — only listed packages will be allowed. All others are blocked.

### Method 2: Phenotype Blocklist (Per-Installer)

Add the installer package to `AppValidation__blocked_packages_by_installer`. All apps installed by that installer are blocked.

### Method 3: Validation Bypass

Set `AppValidation__should_bypass_validation` to `false`. This would break all apps though — not recommended.

### Method 4: Feature Gate

Set `ProjectedAppsFeature__enabled` to `false`. This disables all phone-projected apps from appearing in the launcher.

### Method 5: Per-Category Filters

For navigation apps: Add to `ControlSupportedNavApps__denylisted_nav_packages`
For messaging apps: Add to `Messaging__app_block_list`
For cradle: Add to `CradleFeature__app_launcher_package_list` exclusion list

### Method 6: SharedPreferences (Temporary)

Set `allow_unknown_sources` to `false` to block non-Play Store apps. This is a user-facing toggle, not a developer toggle.

### Method 7: CarProjectionValidator

Set `CarProjectionValidator__include_enabled_only_components` to `true` and ensure the target app's manifest declares `android:enabled="false"` on its `CarAppService`.

---

## How to Add an App to the Launcher

1. Ensure `ProjectedAppsFeature__enabled` is `true`
2. If `AppValidation__allowed_package_list` is set, add the app's package name to it
3. Ensure the app's package is NOT in `AppValidation__blocked_packages_by_installer`
4. Ensure `AppValidation__should_bypass_validation` is `false` (or your package is properly validated)
5. Ensure `CarProjectionValidator__include_enabled_only_components` is `false`, OR the app's manifest doesn't declare `android:enabled="false"`
6. If using category-specific filters (navigation, cradle, etc.), ensure the app is in the appropriate allowlist or not in the denylist

---

## OEM All Vehicle Apps

The "All vehicle apps" screen (listing apps installed on AAOS, not phone-projected) is controlled by:

- **Manifest registration**: `com.google.android.projection.gearhead.oem.AllCarAppsService` with `CATEGORY_PROJECTION_OEM`
- **UI routing**: The `OEM_FACET` string constant and `OEMExitService` control entry into this view
- **No phenotype flags found** specifically for this feature in the gearhead codebase
- Visibility of individual car-side apps is likely controlled by the **OEM's own AppDecorService** and component manifest declarations
- The `CarAppLibrary__cluster_force_disable_package_list` can filter apps from appearing in the **cluster display** specifically

---

## Developer Dump Tool (dlq.smali)

The `Ldlq` class appears to be a debug/diagnostic tool that outputs the current state of all flags to a `PrintWriter`. It reads each flag and calls `Ldlq;->K(...)` to print the output. This is likely invoked through a developer settings menu or shell command to display all active configuration.

Key flag groups dumped:
- AppValidation flags (lines 700-750)
- CarProjectionValidator flags (lines 2960-3010)
- LauncherShortcuts flags (lines 6295-6330)
- ProjectedAppsFeature / RemoteCarApps flags (lines 8260-8630)
- All other phenotype flags via feature gate readers

To dump all flags at runtime: Find the code path that calls `dlq` methods with a `PrintWriter` (likely via `adb shell dumpsys` or a settings debug screen).