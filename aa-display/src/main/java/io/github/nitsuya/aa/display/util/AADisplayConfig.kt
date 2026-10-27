package io.github.nitsuya.aa.display.util

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import com.github.kyuubiran.ezxhelper.utils.tryOrNull
import java.util.Properties

/**
 * All configuration keys for AADisplay.
 *
 * Usage:
 *   AADisplayConfig.LauncherPackage.get(prefs)
 *   AADisplayConfig.LauncherPackage.put(editor, value)
 */
object AADisplayConfig {
    const val ConfigName = "aadisplay_config"

    // Mirror / launcher
    val LauncherPackage = StringCfg("LauncherPackage", "com.autonavi.amapauto")
    val HomePackage = StringCfg("HomePackage", "com.ss.squarehome2")
    val AutoOpen = BooleanCfg("AutoOpen", true)
    val VirtualDisplayDpi = IntCfg("VirtualDisplayDpi", 0)
    val AndroidAutoDpi = IntCfg("AndroidAutoDpi", 0)
    val DelayDestroyTime = IntCfg("DelayDestroyTime", 180)
    val ScreenOffReplaceLockScreen = BooleanCfg("ScreenOffReplaceLockScreen", false)
    val CloseLauncherDashboard = BooleanCfg("CloseLauncherDashboard", true)
    val ForceRightAngle = BooleanCfg("ForceRightAngle", true)
    val DisplayImePolicy = IntCfg("DisplayImePolicy", 1)
    val VoiceAssistShell = StringCfg("VoiceAssistShell", null)
    val CreateVirtualDisplayBefore = StringArrayCfg("CreateVirtualDisplayBefore")
    val DestroyVirtualDisplayAfter = StringArrayCfg("DestroyVirtualDisplayAfter")
    val ComGoogleAndroidGmsCarProps = PropertiesCfg("ComGoogleAndroidGmsCarProps")
    val ComGoogleAndroidProjectionGearheadProps = PropertiesCfg("ComGoogleAndroidProjectionGearheadProps")
    val LauncherModeProps = PropertiesCfg("LauncherModeProps")

    // Head Unit Compatibility Hack
    val EnableHeadUnitHack = BooleanCfg("EnableHeadUnitHack", false)
    val HeadUnitMake = StringCfg("HeadUnitMake", "Mobis")
    val HeadUnitModel = StringCfg("HeadUnitModel", "standard_m_5")
    val ForceAllowUnknownSources = BooleanCfg("ForceAllowUnknownSources", true)
    val BypassValidation = BooleanCfg("BypassValidation", true)

    // Coolwalk layout preference
    val PreferCoolwalkLayout = BooleanCfg("PreferCoolwalkLayout", false)

    // Driver distraction
    val DisableSpeedbump = BooleanCfg("DisableSpeedbump", false)


    // --- config backup version tracking ---
    val ConfigVersion = IntCfg("_config_version", 0)

    // List of all configs for backup/restore
    val all: List<Cfg<*>> = listOf(
        LauncherPackage, HomePackage, AutoOpen, VirtualDisplayDpi, AndroidAutoDpi,
        DelayDestroyTime, ScreenOffReplaceLockScreen, CloseLauncherDashboard,
        ForceRightAngle, DisplayImePolicy, VoiceAssistShell,
        CreateVirtualDisplayBefore, DestroyVirtualDisplayAfter,
        ComGoogleAndroidGmsCarProps, ComGoogleAndroidProjectionGearheadProps,
        LauncherModeProps,
        EnableHeadUnitHack, HeadUnitMake, HeadUnitModel,
        ForceAllowUnknownSources, BypassValidation,
        PreferCoolwalkLayout,
        DisableSpeedbump,
    )
}
