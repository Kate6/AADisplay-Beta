# AGENTS.md — Android Auto (Gearhead) Disassembled APK

## Project Overview

- **Package**: `com.google.android.projection.gearhead` (codename: "Gearhead")
- **App Name**: Android Auto
- **APK Version**: 168161804 (16.8.161804-release.daily, "CinnamonBun")
- **Disassembly Tool**: apktool 2.11.1
- **SDK**: minSdk 32 (Android 13), targetSdk 37 (Android 16), compileSdk 37
- **Car API Level**: 8 (from `unknown/car-app-api.level`)

> **Warning**: This is a **disassembled smali APK**, not source code. All Java/Kotlin source has been converted to obfuscated Dalvik bytecode (smali). Classes use single/two-letter obfuscated names. Do not expect meaningful class/method names — you'll need to search by package path, service declarations, or string constants to find what you need.

---

## Directory Structure

```
base/
├── AndroidManifest.xml          # Disassembled manifest
├── apktool.yml                  # Build config (minSdk 32, targetSdk 37)
├── res/                         # Disassembled resources (layouts, strings, drawables, etc.)
├── assets/                      # Compiled assets (AltFormats_*, Metadata_*, phenotype configs)
├── smali/                       # Primary DEX — ~7935 files (all non-framework smali)
├── smali_classes2/             # Secondary DEX — ~8237 files
├── smali_classes3/            # Tertiary DEX — ~8734 files
├── smali_classes4/             # Quaternary DEX — mostly desugar/j$ utilities
├── unknown/                     # Unknown type files (car-app-api.level, javax, kotlin, etc.)
└── original/                    # Original APK metadata (manifest, certs)
```

---

## Key Package Paths

### Gearhead Core
- `com.google.android.projection.gearhead` — Main package
  - `GhApplication` — Application class (entry point)
  - `service/GearheadService` — Core service
  - `system/` — System services (lifecycle, projection, app launcher, etc.)
  - `companion/` — Phone-side companion app components (settings, devsettings)
  - `companion/settings/DefaultSettingsActivity` — Settings launcher

### Car App Components (`com.google.android.apps.auto`)
- `components/carhome/` — Car home UI (ProjectedHomeActivity, ProjectedHomeService)
- `components/frx/` — Phone-to-car pairing/FRX flow
- `components/connectivity/` — Bluetooth/USB/WiFi connectivity management
- `components/telecom/` — Phone call/Telecom integration
- `components/media/` — Media playback (phone)
- `components/messaging/` — SMS/messaging
- `components/calendar/` — Calendar integration
- `components/apphost/` — Template rendering host
- `components/coolwalk/` — Coolwalk UI system (Compose-based)
- `components/preflight/phone/` — Setup/permission preflight flows
- `components/projectionstate/` — Projection state management
- `components/crossprofile/` — Cross-profile (work/personal) support
- `components/bugreport/` — Bug reporting
- `components/feedback/` — CSAT/HATS feedback collection
- `components/settings/` — In-car settings UI
- `components/demand/` — On-demand suggestions
- `components/firstdrive/` — First drive experience
- `components/ace/` — ACE (Personal Context) insight renderer
- `carservice/gmscorecompat/` — AAOS-side car service
- `carservice/clustersim/` — Instrument cluster simulator
- `carservice/car/` — Car-side (AAOS) services
- `wireless/` — Wireless projection setup

### Car App Library (`com.android.car.libraries`)
- `com/android/car/libraries/apphost/` — Apphost library (template rendering engine)
- Contains: `basewidgets/`, `map/`, `tab/`, `view/`, `external/`, `internal/`

### External Libraries
- `androidx.car.app` — AndroidX Car App library
- `androidx.compose.*` — Jetpack Compose (UI)
- `androidx.lifecycle` — Lifecycle management
- `androidx.room` — Database
- `com.google.android.gms.car` — GMS Car library
- `com.google.android.gms.carsetup` — Car setup flow
- `com.google.android.gms.auth_account_client` — Account auth
- `com.google.android.gms.maps` — Maps API
- `com.bumptech.glide` — Image loading
- `com.google.android.material` — Material Design
- `j$/time/` — Java 8+ time desugaring
- `okhttp3/` — OkHttp networking
- `org/joda/time/` — Joda Time

### Shared/GMS Components
- `com.google.android.gearhead` — Gearhead shared code
  - `appdecor/` — App decoration service
  - `demand/` — Demand client
  - `feedback/` — Crash reporting, feedback
  - `service/SharedService` — Shared service
  - `notifications/` — Notification management
- `com.google.android.enterprise.connectedapps` — Cross-profile connector

---

## Architecture Overview

### Multi-Process Model

The app runs across multiple OS processes, each with a distinct role:

| Process | Purpose |
|---------|---------|
| `:projection` | Main projected UI and host (most services/activities live here) |
| `:shared` | Shared state between phone/car sides |
| `:car` | AAOS (car-side) service and lifecycle |
| `:provider` | Projection state provider |
| `:crossprofile` | Cross-profile (work/personal) handling |
| `:watchdog` | Watchdog/recovery services |
| `:primes_lifeboat` | Performance monitoring |
| Default | Fallback for components without explicit process |

### Control Flow

1. **App Start** → `GhApplication.onCreate()` initializes core DI/state
2. **Projection Start** → `GearheadService` + `GhLifecycleService` manage projection lifecycle
3. **Setup Flow** → `SetupService` or FRX activities for first-time pairing
4. **In-Car UI** → `ProjectedHomeService` + `ProjectedHomeActivity` provide car home
5. **App Hosting** → `TemplateService` hosts 3rd-party car apps via `CarAppService`
6. **Phone Call** → `TelecomService` + `CarProjectionInCallServiceImpl`
7. **Media** → `MediaService` for media browser

### Template System (CarAppService Host)

Third-party car apps connect via `androidx.car.app.CarAppService`. The gearhead app hosts them through:
- `TemplateService` — Standard templates
- `TemplateNavigationService` — Navigation-specific templates
- `TemplateClusterService` — Instrument cluster display
- `TemplateAuxiliaryDisplayService` — Auxiliary display
- `GhostActivity` — Minimal activity shell for hosted apps

---

## Working with Smali

### Class Naming Conventions

All class names are obfuscated. The pattern is:
- Single letter (`a.smali`, `b.smali`) to 2-3 letter (`ab.smali`, `abc.smali`)
- Suffixes like `a$1` for anonymous inner classes, `a$b` for named inner classes
- External synthetic classes: `ad$$ExternalSyntheticApiModelOutline0.smali`

To find classes, search by:
1. **Package path** (directory structure maps directly to package names)
2. **String constants** inside smali files (e.g., `"GH.GearheadService"`)
3. **Manifest declarations** — activities, services reference fully-qualified class names

### Smali Patterns

```smali
# Class declaration
.class public Lcom/google/android/projection/gearhead/service/GearheadService;
.super Landroid/app/Service;

# Static field
.field private static final b:Lyit;

# Instance field
.field a:Z

# Direct method
.method public constructor <init>()V
    .locals 1
    invoke-direct {p0}, Landroid/app/Service;-><init>()V
    return-void
.end method

# Virtual method
.method protected onCreate()V
    .locals 1
    invoke-super {p0}, Landroid/app/Service;->onCreate()V
    return-void
.end method

# String constant logging pattern
const-string v0, "GH.GearheadService"
invoke-static {v0}, Lyit;->l(Ljava/lang/String;)Lyit;  # Log.d wrapper
```

### Field/Method Obfuscation

- Most fields are single-letter: `a`, `b`, `c`, `d`, etc.
- Some classes prefix fields with synthetic names: `b`, `c`, `d`, `e`
- The static field `b` in `GhApplication` stores the app instance

---

## Build & Rebuild

### Rebuilding the APK

This is a **disassembled** APK. To rebuild:

```bash
# Using apktool
apktool b base -o base_rebuilt.apk

# Then sign the APK (required for installation)
# Use apksigner or uber-apk-signer
```

### Key apktool.yml Settings

```
version: 2.11.1
minSdkVersion: 32
targetSdkVersion: 37
versionCode: 168161804
versionName: 16.8.161804-release.daily
sparseResources: true
```

> **Note**: `sparseResources: true` means many resources use sparse binary format. Some resource editors may not handle them correctly.

### Critical Files Not to Modify

- `original/` — Contains original APK metadata and signing certificates
- `assets/dexopt/` — Pre-computed DEX optimization profiles (baseline.prof, baseline.profm)
- `unknown/car-app-api.level` — Car API level indicator (value: 8)

---

## Important Patterns

### Service Registration in Manifest

Services declare intent filters for Car API categories:

```xml
<service android:name="..." android:process=":projection">
    <intent-filter>
        <category android:name="com.google.android.gms.car.category.CATEGORY_PROJECTION"/>
    </intent-filter>
    <meta-data android:name="com.google.android.projection.gearhead.GHOST_ACTIVITY"
        android:value="com.google.android.apps.auto.client.activity.ghost.GhostActivity"/>
</service>
```

### Car API Version Requirements

- `androidx.car.app.minCarApiLevel` = 1 (in manifest `<meta-data>`)
- `androidx.car.app.minApiLevel` = 1
- `car-app-api.level` file = 8

### Phenotype/Feature Flags

Feature flags are configured via binary protobuf files in `res/raw/`:
- `com_google_android_projection_gearhead_registration_info_*.binarypb`
- Release, debug, dogfood, and experimental variants

### Logging Pattern

Classes typically create a static `Lyit` (Logger) field:
```smali
const-string v0, "GH.ClassName"
invoke-static {v0}, Lyit;->l(Ljava/lang/String;)Lyit;
sput-object v0, Lpath/to/Class;->b:Lyit;
```

### Notification Channels

The app creates notification channels for various subsystems (projection, phone calls, messaging, etc.).

---

## Testing & Modification Notes

### Modifying Resources

- XML resources are fully readable — edit in-place
- String resources in `res/values/strings.xml`
- Layout resources in `res/layout/`
- Themes in `res/values/styles.xml` and `res/values-*/styles.xml`

### Modifying Smali

- Changes to smali affect DEX files directly
- After modification, rebuild with `apktool b`
- Renaming classes or changing method signatures may break inter-DEX references — test thoroughly

### Multi-DEX

Four DEX files provide method count separation:
- `smali/` — App code + gearhead core
- `smali_classes2/` — Car libs, Glide, personal context
- `smali_classes3/` — Additional libraries
- `smali_classes4/` — Desugar utilities (java.time, java.nio, etc.)

### APK Signing

After rebuild, the APK must be signed. The original APK uses:
- V1 + V2 + V3 APK signing scheme
- Google production signing key (do not distribute)

---

## Resource Organization

- `res/values/` — Core resources (strings, colors, dimens, styles)
- `res/values-v3X/` — Version-specific overrides (v30, v31, v32, v33, v34, v35, v36, v37)
- `res/values-wXXXXdp/` — Screen width buckets
- `res/values-swXXXXdp/` — Smallest width buckets
- `res/values-wheel/` — Steering wheel-specific dimensions
- `res/values-watch/` — Watch/cluster-specific dimensions
- `res/xml/` — XML configs (providers, preferences, network security)
- `res/anim/` — Animation resources
- `res/color-*/` — Color state lists
- `res/mipmap-*/` — App icons

---

## Dependencies (from embedded libraries)

- **AndroidX Car App Library** — Car app hosting API
- **Jetpack Compose** — UI framework (Coolwalk)
- **Google Play Services** — GMS, auth, maps, car, analytics
- **Glide** — Image loading
- **OkHttp + gRPC** — Networking
- **Room** — Database
- **Joda Time** — Date/time utilities
- **kotlin-stdlib** — Kotlin runtime with desugaring
- **Material Components** — Material 3 design
- **androidx.window** — Window extensions for car displays

---

## Phenotype Database & Feature Flags

This APK contains an extensive **GMS Phenotype/feature flag system** that controls nearly every aspect of the app's behavior. There are **five distinct flag mechanisms**, each with different sources and update frequencies.

### Flag Mechanism 1: AssistantFeatureFlags (gRPC — via Assistant Service)

**Source**: Values returned from `GetAssistantFeatureFlags` gRPC call to the Assistant (package `com.google.android.libraries.assistant.auto.jumpboost.gearhead.grpc.endpoint.CarAssistantService`). These are **not** from the GMS Phenotype database.

**Data class**: `Lolb` (`smali/olb.smali`) — a protobuf-generated class with 14 boolean fields and 1 integer field.

| Field | Flag Name | Purpose |
|-------|-----------|---------|
| `Lolb.c` | `coolwalkAssistantSuggestionsEnabled` | Enable assistant suggestions in Coolwalk dashboard |
| `Lolb.d` | `feedbackInitializationIntentEnabled` | Enable feedback intent initialization |
| `Lolb.e` | `dodgeboostEnabled` | Enable Dodgeboost (phone↔car quick handshake) |
| `Lolb.f` | `coolwalk_direct_action_enabled` | Enable direct actions in Coolwalk |
| `Lolb.g` | `coolwalk_streamed_media_recommendations_enabled` | Enable streamed media recommendations |
| `Lolb.h` | `media_recommendation_callbacks_service_enabled` | Enable media recommendation callback service |
| `Lolb.i` | `notification_proto_enabled` | Enable notification protocol |
| `Lolb.j` | `send_message_to_contact_using_provider_client_input_enabled` | Enable provider-client input for messaging |
| `Lolb.k` | `gearhead_smart_action_enabled` | Enable smart actions |
| `Lolb.l` | `kitt_enabled` | Enable KITT (voice assistant) |
| `Lolb.n` | `kitt_eligible` | KITT eligibility |
| `Lolb.o` | `car_control_enabled` | Enable car controls |
| `Lolb.b` | *(version int)* | Protocol version field |

**How to find usage**: Search for `Lolb;->[c-o]:Z` pattern across smali files:
- `Lolb;->c:` — Coolwalk suggestions (used in `ktc.smali:6288`, `kta.smali:1901`)
- `Lolb;->e:` — Dodgeboost (used in `ssg.smali:870`)
- `Lolb;->h:` — Media rec callbacks (used in `ssg.smali:2142`)
- `Lolb;->i:` — Notification proto (used in `ssg.smali:5086`)
- `Lolb;->j:` — Send message (used in `ktc.smali:2471`)
- `Lolb;->k:` — Smart actions (used in `ssg.smali:1607`)
- `Lolb;->l:` — KITT (used in `kta.smali:140`, `kta.smali:973`)
- `Lolb;->m:` — (used in `kta.smali:202`)
- `Lolb;->n:` — KITT eligible + car controls (used in `ktc.smali:2372`, `ktc.smali:3985`)
- `Lolb;->o:` — Car control (sent to assistant in `kih.smali:456`)

---

### Flag Mechanism 2: GMS Phenotype Database (1072+ flags)

**Source**: GMS Phenotype/Prefs database. Flags are in the format `Category__flag_name` (double underscore separator).

**How to read**: Via `Prefs.getBooleanFlagValue(name, default)`, `Prefs.getStringFlagValue()`, etc. in the GMS library. The `FlagUpdaterReceiver` (`smali_classes2/com/google/android/apps/auto/components/config/phenotype/receiver/FlagUpdaterReceiver.smali`) triggers flag synchronization on app update (`android.intent.action.MY_PACKAGE_REPLACED`).

**Log sources**: `CAR`, `GEARHEAD_ANDROID_PRIMES`

#### Key Flag Categories (all in `/smali/`):

**Assistant/AI flags** (`Assistant__*`):
```
Assistant__dodgeboost_initial_handshake_enabled — initial Dodgeboost handshake
Assistant__gemini_barge_in_enabled_kill_switch — kill switch for Gemini barge-in
Assistant__gsa_dismisses_on_nav_active_start_enabled
Assistant__quick_feedback_throttling_enabled
Assistant__voice_session_manager_handles_asr_enabled
Assistant__supported_api_versions
Assistant__upgrade_model_notification_enabled
Assistant__upgrade_model_notification_alpha_enabled
Assistant__assistant_message_summarization_quick_feedback_throttling_enabled
Assistant__has_latest_robin_model
Assistant__robin_model_notification_max_count
Assistant__robin_model_notification_triggering_rate
Assistant__transcription_character_limit
Assistant__grpc_retry_* (backoff_multiplier, initial_backoff_seconds, max_attempts, max_backoff_seconds)
Assistant__enable_feedback_after_thumbs_down
Assistant__interrupt_live_on_short_search_key_press_kill_switch
Assistant__disable_scrim_for_voicesearch_kill_switch
Assistant__enable_waive_priority_bind
Assistant__gemini_live_dashboard_focus_delay
Assistant__education_delay_milliseconds
Assistant__api_version_override
AssistantTesting__projected_enabled
AceFeature__* — ACE (Personal Context) insight rendering
AceFeature__mode, AceFeature__maximum_ace_wait_time
AceFeature__fake_insight_generation, AceFeature__publish_hint_on_startup
AceFeature__add_entity_type_label
```

**Coolwalk UI flags** (`Coolwalk__*`):
```
Coolwalk__assistant_suggestions_enabled — assistant suggestions in dashboard
Coolwalk__supported_navigation_apps — comma-separated nav app package list
Coolwalk__choose_assistant_suggestion_over_app_suggestion
Coolwalk__compose_catalog_enabled — compose catalog
Coolwalk__dashboard_show_3p_notifications_with_actions_enabled
Coolwalk__dashboard_notifications_refresh_interval_seconds
Coolwalk__dashboard_top_card_debounce_milliseconds
Coolwalk__dashboard_top_card_rate_limit_milliseconds
Coolwalk__enable_gm3_icon_tinting
Coolwalk__feedback_notification_timeout_seconds
Coolwalk__media_rec_card_timeout_in_millis
Coolwalk__media_rec_request_signal_to_assistant_enabled
Coolwalk__mediacard_session_timeout_in_millis
Coolwalk__messaging_notification_timeout_seconds
Coolwalk__messaging_notification_priority_threshold_seconds
Coolwalk__nav_app_border_width
Coolwalk__navigation_signal_to_assistant_enabled
Coolwalk__neutralized_theme_kill_switch
Coolwalk__new_compose_touch_slop
Coolwalk__only_process_car_configuration_change
Coolwalk__use_light_dark_theme, Coolwalk__use_light_dark_theme_focus_input
Coolwalk__use_phone_primary_color
Coolwalk__lazy_assistant_media_rec_service_registration_enabled
Coolwalk__rail_widget_user_education_is_dry_run
Coolwalk__dashboard_unavailable_user_education_is_dry_run
Coolwalk__fix_notification_card_alignment_kill_switch
Coolwalk__rounded_corner_mask_display_specific_denylist
Coolwalk__update_badge_color_kill_switch
Coolwalk__live_card_dashboard_visibility_debounce_milliseconds
```

**Car App Library flags** (`CarAppLibrary__*` — 60+ flags, controls the car app template rendering):
```
CarAppLibrary__compose_grid_template_emitter_enabled
CarAppLibrary__compose_list_template_emitter_enabled
CarAppLibrary__compose_menu_template_emitter_enabled
CarAppLibrary__compose_search_template_emitter_enabled
CarAppLibrary__compose_sectioned_item_template_emitter_enabled
CarAppLibrary__compose_web_view_template_emitter_enabled
CarAppLibrary__compose_grid_wrapper_template_emitter_enabled
CarAppLibrary__compose_unified_action_enabled
CarAppLibrary__grid_template_fab_enabled
CarAppLibrary__lazy_icon_enabled
CarAppLibrary__lazy_icon_in_gemini_enabled
CarAppLibrary__miniplayer_overlay_enabled
CarAppLibrary__multi_transaction_list_loading_enabled
CarAppLibrary__place_list_map_template_in_compose_enabled
CarAppLibrary__enable_pane_template_in_compose
CarAppLibrary__enable_tab_template_in_compose
CarAppLibrary__enable_map_with_content_in_compose
CarAppLibrary__header_unified_action_enabled
CarAppLibrary__map_unified_action_enabled
CarAppLibrary__nav_unified_action_enabled
CarAppLibrary__media_unified_action_enabled
CarAppLibrary__message_pane_sign_in_unified_action_enabled
CarAppLibrary__apphost_unified_app_icon_action_enabled
CarAppLibrary__emitter_persistent_ui_state_enabled_kill_switch
CarAppLibrary__speedbump_compose_enabled_kill_switch
CarAppLibrary__contrast_check_text_in_compose_enabled_kill_switch
CarAppLibrary__hacky_disappearing_focus_in_compose_interop_fix_enabled_kill_switch
CarAppLibrary__skip_car_text_view_pre_draw
CarAppLibrary__skip_car_text_view_pre_draw_logic_for_empty_variants
CarAppLibrary__conversation_item_enable_custom_actions_ui
CarAppLibrary__app_driven_refresh_enabled
CarAppLibrary__app_driven_refresh_enabled_categories
CarAppLibrary__app_driven_refresh_enabled_for_undefined_category
CarAppLibrary__app_driven_refresh_delay_in_milliseconds
CarAppLibrary__template_update_throttle_in_milliseconds
CarAppLibrary__return_cached_negotiated_api
CarAppLibrary__cluster_force_disable_package_list
CarAppLibrary__tab_layout_orientation
CarAppLibrary__max_list_size_with_speedbump
CarAppLibrary__max_long_list_size_with_speedbump
CarAppLibrary__skip_loading_spinner_for_menu_template_first_chunk_kill_switch
CarAppLibrary__move_focus_if_shown_kill_switch
CarAppLibrary__moderation_exempt_kill_switch
CarAppLibrary__use_correct_speedbump_manager_kill_switch
CarAppLibrary__timer_persistence_kill_switch
CarAppLibrary__rethrow_internal_app_exceptions_enabled
CarAppLibrary__batch_template_telemetry_kill_switch
CarAppLibrary__attach_alert_notification_flow_to_lifecycle
CarAppLibrary__task_limit_restrictions_allows_overflow
```

**Car Activity Manager flags** (`CarActivityManager__*`):
```
CarActivityManager__restart_timeout_handler_enabled
CarActivityManager__forward_keycode_tel_to_gearhead
CarActivityManager__fail_resume_for_no_drawing_spec_kill_switch
CarActivityManager__fail_resume_for_wrong_state_kill_switch
CarActivityManager__cancel_pending_lifecycle_events_for_finish
CarActivityManager__use_pending_video_config_kill_switch
CarActivityManager__check_start_info_for_fallback_kill_switch
CarActivityManager__gmm_start_sub_phase_timers_hu_make_allow_list
CarActivityManager__interaction_log_packages
CarActivityManager__interaction_log_command_codes
CarActivityManager__latency_log_command_codes
```

**Cielo/Earth/Dashboard** (`CieloFeature__*`, `CradleFeature__*`, `Boardwalk__*`):
```
CieloFeature__earth_enabled — Earth (dashboard) enabled
CieloFeature__earth_focus_enabled
CieloFeature__earth_shade_enabled
CieloFeature__earth_car_window_enabled
CieloFeature__earth_companion_enhanced_ui_enabled
CieloFeature__earth_dashboard_widget_combo_enabled
CieloFeature__cielo_focus_enabled
CieloFeature__cielo_status
CieloFeature__earth_status
CieloFeature__earth_featured_widgets
Boardwalk__news_browser_available
Boardwalk__start_content_window_animation_timeout_ms
CradleFeature__immersive_mode_enabled
CradleFeature__day_night_enabled
CradleFeature__extended_toolbar_enabled_cars
CradleFeature__app_controlled_immersive_mode_enabled
CradleFeature__all_app_launcher_enabled
```

**Audio/Video flags** (`AudioBufferingFeature__*`, `AudioFocus__*`, `AudioFlowControl__*`, `VideoEncoderParams__*`, `VideoTimingControllerFeature__*`):
```
AudioBufferingFeature__minimum_audio_buffers_for_wifi / _usb / _wifi_navigation
AudioBufferingFeature__use_min_audio_buffer_calculator_kill_switch
AudioBufferingFeature__ackless_mode_minimum_audio_buffers_kill_switch
AudioFocus__hold_channels_for_asr
AudioFocus__loss_asr_stop_delay_ms
AudioFlowControl__throttling_disabled
AudioFlowControl__use_synchronous_audio_processor
AudioPolicy__additional_media_stream_audio_usages_list
VideoEncoderParams__adaptive_bitrate_* / force_software_codec / key_frame_interval_*
VideoEncoderParams__enable_roi_encoding, VideoEncoderParams__roi_qp_offset
VideoTimingControllerFeature__enable_video_timing_controller
VideoTimingControllerFeature__enable_video_timing_controller_parked_exp
VideoDiagnosticsFeature__audio_latency_histogram_intervals_ms
```

**Connectivity/Wireless** (`WirelessProjectionInGearhead__*` — 80+ flags):
```
WirelessProjectionInGearhead__enable_dual_sta
WirelessProjectionInGearhead__disable_dual_sta_when_hotspot
WirelessProjectionInGearhead__use_rfcomm_transport_for_spark
WirelessProjectionInGearhead__use_wpa3_when_available
WirelessProjectionInGearhead__skip_frx_check_in_wsem
WirelessProjectionInGearhead__start_from_notification_enabled
WirelessProjectionInGearhead__associate_bt_devices_and_trigger_wireless_from_cdm
WirelessProjectionInGearhead__wpp_first_message_timeout_ms / _for_dongle
WirelessProjectionInGearhead__acl_connect_event_timeout
WirelessProjectionInGearhead__car_absence_timeout_ms
WirelessProjectionInGearhead__max_network_connection_attempts / _duration
WirelessProjectionInGearhead__require_hfp_for_spark
WirelessProjectionInGearhead__throw_exception_on_illegal_state_of_wpp_first_message_timeout_kill_switch
WirelessProjectionInGearhead__disable_broadcast_receiver_for_non_system_user
WirelessProjectionInGearhead__log_connectivity_event_for_ping_health_kill_switch
WirelessProjectionInGearhead__wifi_projection_protocol_head_unit_denylist
WirelessProjectionInGearhead__predefined_frequency_list
WirelessProjectionInGearhead__wifi_frequency_saturation_coefficient
WirelessProjectionInGearhead__restart_wpp_on_ping_timeout
WirelessProjectionInGearhead__restart_wpp_for_network_failure_delay
WirelessProjectionInGearhead__send_setup_event_before_network_disconnect_on_bye_bye_kill_switch
WirelessBabysitter__updated_lifecycle_notifications_are_enabled
WirelessSettings__is_car_details_screen_enabled
WirelessSettings__reload_after_connect_a_car
WirelessMonitorMode__package_allow_list_proto
WirelessLatencyMonitor__probe_interval_ms / _report_interval_ms / _warning_threshold_ms
WirelessHttpProxy__enabled, WirelessHttpProxy__host_whitelist
```

**Bluetooth** (`BluetoothPairing__*`):
```
BluetoothPairing__prevent_rebonding_during_wired_projection
BluetoothPairing__prevent_rebonding_during_wireless_projection
BluetoothPairing__disable_a2dp
BluetoothPairing__car_bluetooth_service_disable
BluetoothPairing__no_force_enable_a2dp_kill_switch
BluetoothPairing__check_bluetooth_profile_util_is_initialized_kill_switch
```

**USB Babysitter** (`UsbBabysitter__*`, `UsbBabysitterFeature__*`):
```
UsbBabysitter__default_usb_function
UsbBabysitter__switch_usb_function_on_bluetooth_connect
UsbBabysitter__switch_usb_function_on_usb_disconnect
UsbBabysitter__enable_a2dp_at_projection_end
UsbBabysitter__usb_recovery_functions / _v2
UsbBabysitter__a2dp_fix_car_list
UsbBabysitterFeature__use_new_reset_api_for_r
UsbBabysitterFeature__usb_mode_switch_q3_updates
UsbBabysitterFeature__usb_mode_switch_initial_accessory_timeout_ms
UsbBabysitterFeature__usb_mode_switch_disable_token
```

**Car Sensor/Telemetry** (`CarSensorParameters__*`, `AudioDiagnosticsFeature__*`, `VideoDiagnosticsFeature__*`):
```
CarSensorParameters__max_parked_speed_gps_sensor / _wheel_sensor
CarSensorParameters__stop_flp_injection
CarSensorParameters__refine_car_sensor_rate_kill_switch
CarSensorParameters__enable_rate_throttler_jitter_allowance_kill_switch
CarSensorParameters__sensor_types_to_rate_throttle_at_delivery_phase
AudioDiagnosticsFeature__report_audio_latency_stats_interval_ms
AudioDiagnosticsFeature__log_audio_latency_stats_telemetry_threshold_wifi / _usb
```

**App Validation/Projection** (`AppValidation__*`, `CarProjectionValidator__*`):
```
AppValidation__should_bypass_validation
AppValidation__dhu_bypass_validation
AppValidation__allowed_package_list
AppValidation__blocked_packages_by_installer
AppValidation__swallow_play_api_exception
CarProjectionValidator__include_enabled_only_components
CarProjectionValidator__log_reason_apps_not_allowed_all_apps
CarProjectionValidator__measure_latency_enabled
```

**Connection Handoff** (`CarConnectionHandoffFeature__*`):
```
CarConnectionHandoffFeature__should_fall_back_to_gms_core
CarConnectionHandoffFeature__never_fall_back_to_gms_core
CarConnectionHandoffFeature__validate_start_connection_intents_via_handshake
CarConnectionHandoffFeature__check_frx_timeout_ms
CarConnectionHandoffFeature__bypass_first_activity
CarConnectionHandoffFeature__handoff_handlers
```

**Content/Media Browse** (`ContentBrowse__*`):
```
ContentBrowse__sixtap_force_enabled
ContentBrowse__speedbump_force_enabled
ContentBrowse__keyboard_force_disabled
ContentBrowse__drawer_default_allowed_taps_touchpad
ContentBrowse__enable_speed_bump_projected
ContentBrowse__permits_chart
```

**Apollo (Media)** (`ApolloFeature__*`):
```
ApolloFeature__expressive_enabled
ApolloFeature__no_auto_swipe_to_suggestions_kill_switch
ApolloFeature__limit_one_playback_only_app_per_package_kill_switch
ApolloFeature__ignore_invalid_active_sessions_kill_switch
ApolloFeature__ignore_unplayed_buffering_sessions_kill_switch
ApolloFeature__recycled_bitmap_check_kill_switch
ApolloFeature__filter_media_sessions_outside_of_fragment_kill_switch
ApolloFeature__bitmap_live_data_use_copy_kill_switch
ApolloFeature__decrease_pager_snap_threshold_kill_switch
```

**User Education/Tooltips** (`UserEducation__*`):
```
UserEducation__assistant_tooltip_first_run_enabled / _is_dry_run / _image / _copy
UserEducation__assistant_tooltip_launcher_open_iteration_enabled / _string / _is_dry_run
UserEducation__assistant_tooltip_media_open_iteration_string
UserEducation__assistant_tooltip_nth_run_enabled / _count / _copy / _image / _is_dry_run
UserEducation__assistant_tooltip_phone_call_ended_enabled / _is_dry_run
UserEducation__assistant_tooltip_start_of_navigation_is_dry_run / _text_copy / _threshold_seconds
UserEducation__launcher_tooltip_nth_run_enabled / _count / _is_is_dry_run
UserEducation__notification_tooltip_first_run_enabled / _is_dry_run
UserEducation__notification_tooltip_new_notification_nth_run_enabled / _count / _is_dry_run
UserEducation__assistant_tooltip_dialer_first_open_enabled / _is_dry_run
UserEducation__send_gearhead_event_enabled
UserEducation__start_of_drive_opportunity_delay
UserEducation__roca_classic_upgrade_tooltip_first_run_is_dry_run
```

**Bugreport** (`Bugreport__*`):
```
Bugreport__delayed_api_enabled
Bugreport__allow_trigger_from_hu
Bugreport__better_bug_hotlist_ids
Bugreport__user_education_is_dry_run
Bugreport__user_education_nth_run_count
Bugreport__min_minutes_between_requests
Bugreport__keep_bugreport_on_retrieval
```

**Voice Feedback** (`VoiceInternalFeedback__*`, `HatsLapseDetector__*`):
```
VoiceInternalFeedback__enabled
HatsLapseDetector__enabled
Csat__proof_mode_survey
Csat__assistant_csat_enabled
```

**Watevra** (core app library) (`Watevra__*`):
```
Watevra__disable_alphajump
Watevra__foreground_search_fab_enabled
Watevra__foreground_search_fab_component_denylist
Watevra__speedbump_enabled
Watevra__speedbump_map_interactivity_enabled
Watevra__transcription_enabled
Watevra__hide_minimized_state_template_view_when_on_stop_called
Watevra__primary_actions_on_coolwalk
Watevra__max_grid_list_size / _list_size / _pane_list_size / _route_preview_list_size
Watevra__max_template_stack_size
Watevra__host_min_api_level / _host_max_api_level / _host_car_app_library_latest_api_level
Watevra__projection_state_api_packages_allowed
Watevra__car_hardware_headunit_filter_list
Watevra__external_component_filter
Watevra__use_3p_turn_icon_in_cluster_but_not_hud_enabled
```

**Other notable flags**:
```
COOLWALK_UI_ENABLED — master toggle for Coolwalk UI (read in mjd.smali:1215)
car_control_enabled — controls car control feature (Lolb.o)
DODGEBOOST_GET_ASSISTANT_FEATURE_FLAGS_REMOTE_EXCEPTION — Dodgeboost remote exception tracking
GEARHEAD_FIRST_PROCESS_STABLE_FLAG_READ — first flag read tracking
CAR_API_GET_BOOLEAN_FLAG / _INT_FLAG / _STRING_FLAG / _DOUBLE_FLAG — Car API flag access logs
STALE_FLAG_CHECK — stale flag detection
PHENOTYPE_FLAGS_FORCE_SYNC_DISABLED / _ENABLED — force sync override
CLIENT_SIDE_FLAGS — client-side flag override
DebugLogging__enabled
DeepLink__enabled
HeroFeature__enabled
NeoplanFeature__enabled
StratoFeature__enabled
ProjectedAppsFeature__enabled
RemoteCarApps__enabled
WorkAppsFeature__work_apps_enabled
UxPrototype__enabled / _url
IndependentNightModeFeature__enabled
VersionTenFeature__version_ten_launch_param
LauncherShortcuts__enabled
MissingProxyMessagingIntentExtras__enabled
MemoryMonitor__enabled
Dialer__call_availability_error_screen_enabled
Dialer__external_dialer_enabled / _calls_manager_ics_enabled / _multi_sim_tooltip_enabled
BatterySaver__warning_enabled
CollectionBasisVerifierFeatures__enable_cbv_v2 / _google_signature_check / _using_log_verifier_result
```

---

### Flag Mechanism 3: SharedPreferences Configuration Keys

**Source**: `SharedPreferences` files in the app's private storage.

**GearheadConfig** (`gearhead_config` prefs, read via `qaw.smali`):
- `developer_settings_enabled` — developer mode toggle
- `device_supported` — device support check flag
- `device_check_completed` — device check completion flag

**Car Preferences** (`car_preferences` or similar, from `car_preferences.xml`):
- `car_app_mode` — app mode (normal, day/night, etc.)
- `car_force_logging` — force debug logging
- `car_collect_gps_data` — GPS data collection
- `car_disable_anr_monitoring` — disable ANR monitoring
- `car_enable_audio_latency_dump` — audio latency debug
- `car_enable_gal_snoop` — GAL snoop logging
- `car_save_video / _audio / _mic` — media capture toggles
- `car_dump_screenshot` — screenshot dump
- `allow_unknown_sources` — unknown source installation
- `car_video_resolution` — video resolution
- `car_audio_codec` — audio codec
- `failure_injection_enabled` — failure injection (Chaos engineering)
- `debug_overlay_enabled` — debug overlay
- `input_config_enabled` — input configuration
- `touchpad_tuning_enabled` — touchpad tuning
- `touchpad_base_fraction / _min_size_mm / _multimove_penalty_mm` — touchpad parameters
- `touchpad_width / _height` — touchpad dimensions
- `car_day_night_mode` — day/night mode
- `car_display_id` — display ID
- `car_process_pid` — car process PID
- `car_handoff_is_first_connection` — first connection flag
- `car_handoff_legacy_frx_ran` — legacy FRX ran
- `car_module_feature_set` — module feature set
- `car_tos_data / _main / _safety` — ToS state
- `car_handoff_car_info` — car info
- `car_handoff_start_activities` — handoff activity start list
- `car_handoff_user_authorized_projection` — authorization flag
- `car_handoff_car_audio_service_migration_enabled` — audio service migration
- `car_gal_snoop_buffer_size` — GAL snoop buffer
- `car_max_reminder_notification_count` — notification count
- `car_only_connect_to_known_cars` — connect to known cars only
- `device_policy` — device policy
- `car_ev_settings_enabled` — EV settings
- `car_ev_features_enabled` — EV features
- `aa_google_setting_enabled` — AA Google setting
- `google_location_accuracy_enabled` — location accuracy
- `location_enabled` — location
- `toll_card_sensor_enabled` — toll card sensor
- `user_enabled` (weather) — weather user preference
- `optimized_car_activity_enabled` — optimized car activity
- `optimized_car_activity_package_names` — package list
- `accessibility_display_magnification_enabled` — display magnification
- `high_text_contrast_enabled` — high contrast text
- `client_side_throttling_supported` — throttling
- `alpha_jump_language_supported` — alpha jump language
- `touchpad_nav_enabled` — touchpad navigation
- `adb_enabled` — ADB
- `auto_configured_internal` — auto-configured
- `bugreport_multi_display_screenshot_enabled` — multi-display screenshots
- `car_telemetry_enabled` — telemetry
- `car_disable_anr_monitoring` — ANR monitoring
- `car_enable_debug_background` — debug background
- `key_settings_messaging_notifications_enabled` — messaging notifications
- `key_settings_messaging_visual_preview_enabled` — visual preview
- `key_settings_notification_chime_enabled` — notification chime
- `weather_config` — weather config (SharedPreferences)

**Notification channels** (not toggles, but named for clarity):
- `gearhead_alerts` — alerts channel
- `gearhead_connection_status` — connection status channel
- `gearhead_surveys_and_feedback` — surveys/feedback channel
- `gearhead_tips_and_tricks` — tips channel
- `gearhead_importance_high` — high importance

---

### Flag Mechanism 4: Hardcoded Boolean Resources

**Source**: `res/values/bools.xml` (compiled-in defaults, not changeable at runtime via phenotype)

```
enableGSFProvidersInGmsCore = false
enable_more_candidates_popup = true
enable_system_alarm_service_default = false
enable_system_foreground_service_default = true
enable_system_job_service_default = true
gearhead_sdk_supports_window_insets = true
m3_focus_rings_enabled = false
settings_autolaunch_enable_default = true
settings_gemini_barge_in_enabled = true
settings_gemini_barge_in_enabled_fail_safe = false
settings_notification_chime_enable_default = true
settings_work_profile_support_default = false
```

---

### Flag Mechanism 5: Special Top-Level Feature Toggles

These flags are read directly from the GMS Prefs database (not wrapped in `Category__`):

| Flag Name | Location | Purpose |
|-----------|----------|---------|
| `COOLWALK_UI_ENABLED` | `mjd.smali:1215` | Master toggle for entire Coolwalk UI system |
| `car_control_enabled` | `kih.smali:468` | Enable car control (sent to Assistant) |
| `car_ev_settings_enabled` | `v8` | EV settings toggle |
| `car_ev_features_enabled` | `v8` | EV features toggle |
| `optimized_car_activity_enabled` | `v2` | Optimized car activity |
| `touchpad_nav_enabled` | `v2` | Touchpad navigation |
| `alpha_jump_language_supported` | `v2` | Alpha jump language support |
| `client_side_throttling_supported` | `v2` | Client-side throttling |
| `failure_injection_enabled` | `v1` | Failure injection |
| `toll_card_sensor_enabled` | `v0` | Toll card sensor |
| `location_enabled` | `v0` | Location |
| `google_location_accuracy_enabled` | `v0` | Google location accuracy |
| `key_settings_messaging_notifications_enabled` | `v0` | Messaging notifications |
| `key_settings_messaging_visual_preview_enabled` | `v1` | Messaging visual preview |
| `key_settings_notification_chime_enabled` | `v3` | Notification chime |
| `developer_settings_enabled` | `v0` | Developer mode |
| `device_supported` | `v0` | Device support |
| `car_telemetry_enabled` | `v0` | Telemetry |
| `accessibility_display_magnification_enabled` | `v4` | Display magnification |
| `high_text_contrast_enabled` | `v4` | High contrast |
| `adb_enabled` | `v2` / `v3` | ADB enabled |
| `bugreport_multi_display_screenshot_enabled` | `v2` | Multi-display screenshots |
| `auto_configured_internal` | `p0` | Auto-configured |
| `car_disable_anr_monitoring` | `v0` | ANR monitoring |
| `car_enable_debug_background` | `v1` / `v2` | Debug background |
| `car_enable_audio_latency_dump` | `v2` / `v10` | Audio latency dump |
| `car_handoff_is_car_audio_service_migration_enabled` | `v0` / `v2` | Audio service migration |
| `aa_google_setting_enabled` | `v1` | AA Google setting |

---

### Reading/Modifying Flags

**GMS Phenotype flags** are stored in the GMS Core's `Prefs` database. They can be:
1. Read via the `Prefs` API in the GMS library
2. Overridden via `CLIENT_SIDE_FLAGS` override file
3. Force-synced via the `FlagUpdaterReceiver` broadcast

**AssistantFeatureFlags** are stored as proto in the Assistant service, read via gRPC. They can't be directly modified without changing the Assistant service.

**SharedPreferences flags** can be read/written via:
```bash
adb shell "run-as com.google.android.projection.gearhead cat files/shared_prefs/*.xml"
adb shell "run-as com.google.android.projection.gearhead 'cat /data/data/com.google.android.projection.gearhead/shared_prefs/gearhead_config.xml'"
```

**Developer preferences** can be toggled through the in-app settings UI (Developer Settings activity).

**Car Preferences** (on car side) are in `com.google.android.gms` shared prefs.

### Protobinary Registration Files

The phenotype registration info is stored in 4 variants across `res/raw/`:

| File | Variant | Params |
|------|---------|--------|
| `com_google_android_projection_gearhead_registration_info_release.binarypb` | Release | `CAE` |
| `com_google_android_projection_gearhead_registration_info_debug.binarypb` | Debug | `CAQ` |
| `com_google_android_projection_gearhead_registration_info_dogfood.binarypb` | Dogfood | `CAI` |
| `com_google_android_projection_gearhead_registration_info_experimental.binarypb` | Experimental | `CAU` |

These binary protobuf files are compact and contain: package name (`com.google.android.projection.gearhead`) and log source (`GEARHEAD_ANDROID_PRIMES`). The actual flag values come from the server-side Phenotype database, not from these files.

### How to Disable a Feature via Smali Modification

To disable a phenotype-flagged feature:
1. Find the flag name in the smali code
2. Locate where it's read (look for `Prefs.getBooleanFlagValue` equivalents or the `Lolb` flag checking pattern)
3. Replace the result with `false` or `const/4 vX, 0x0`

Example — disable Coolwalk UI:
```
# In mjd.smali around line 1215:
# Find: invoke-virtual {..., "COOLWALK_UI_ENABLED", ...}, Lcom/google/android/gmsPrefs/Prefs;->...
# Replace the resulting boolean check with always-false
```

Example — disable KITT:
```
# In ssg.smali: const/4 v0, 0x0 at line ~1064
# Replace iget-boolean v0, v0, Lolb;->l:Z with const/4 v0, 0x0
```