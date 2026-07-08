package io.github.nitsuya.aa.display.xposed.hook

import android.app.Application
import android.app.Instrumentation
import android.content.SharedPreferences
import android.util.Log
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.init.InitFields
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.nitsuya.aa.display.BuildConfig
import io.github.nitsuya.aa.display.util.AADisplayConfig
import io.github.nitsuya.aa.display.xposed.hook.aa.*
import io.github.nitsuya.aa.display.xposed.log
import org.luckypray.dexkit.DexKitBridge
import kotlin.system.measureTimeMillis


abstract class AaHook {
    companion object {
        const val processMain =       "com.google.android.projection.gearhead"
        const val processProjection = "com.google.android.projection.gearhead:projection"
        const val processCar =        "com.google.android.projection.gearhead:car"
    }
    abstract val tagName: String
    abstract fun isSupportProcess(processName: String) : Boolean
    open fun loadDexClass(bridge: DexKitBridge, lpparam: XC_LoadPackage.LoadPackageParam) {}
    abstract fun hook(config: SharedPreferences, lpparam: XC_LoadPackage.LoadPackageParam)

    /** If true, this hook still runs when mirror is disabled. */
    open val runsWhenMirrorDisabled: Boolean = false

    /**
     * Version-specific resolvers for AA-version-adaptive hooking.
     * Override to provide different implementations per AA version.
     * Default returns a single [LegacyResolver] that works for all versions.
     */
    open val resolvers: List<HookResolver> = listOf(LegacyResolver(this))
}

/** Wraps a legacy [AaHook] as a [HookResolver] for all AA versions. */
class LegacyResolver(private val hook: AaHook) : HookResolver {
    override val range = AaRange(AaVer(0, 0, 0))
    override fun resolve(bridge: DexKitBridge, lpparam: XC_LoadPackage.LoadPackageParam) {
        hook.loadDexClass(bridge, lpparam)
    }
    override fun apply(config: SharedPreferences, lpparam: XC_LoadPackage.LoadPackageParam) {
        hook.hook(config, lpparam)
    }
}

/** Select the best [HookResolver] for [aaVer] from a list. */
fun selectResolver(resolvers: List<HookResolver>, aaVer: AaVer): Selection<HookResolver> {
    val exact = resolvers.filter { aaVer in it.range }
    if (exact.isNotEmpty()) return Selection.Exact(exact.first())
    val best = resolvers.filter { it.range.from <= aaVer }
        .maxByOrNull { it.range.from }
    if (best != null) return Selection.BestEffort(best)
    val fallback = resolvers.minByOrNull { it.range.from }
    if (fallback != null) return Selection.BestEffort(fallback)
    return Selection.None
}

object AndroidAuoHook : BaseHook() {
    override val tagName: String = "AAD_AndroidAuoHook"

    private val allAaHooks = listOf(
        AaBasicsHook, AaSignatureHook, AaDpiHook,
        AaBtnEventHook, AaUiHook, AaPropsHook, AaHeadUnitHackHook,
    )

    private fun activeHooks(processName: String, mirrorEnabled: Boolean): List<AaHook> {
        return allAaHooks.filter { h ->
            if (!h.isSupportProcess(processName)) return@filter false
            if (!mirrorEnabled && !h.runsWhenMirrorDisabled) return@filter false
            true
        }
    }

    override fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        val processName = lpparam.processName

        val configPreferences = XSharedPreferences(BuildConfig.APPLICATION_ID, AADisplayConfig.ConfigName)
        if (!configPreferences.file.canRead()) {
            Log.i(tagName, "load configPreferences fail")
            return
        }
        configPreferences.reload()

        val mirrorEnabled = AADisplayConfig.AutoOpen.get(configPreferences)
        val hooks = activeHooks(processName, mirrorEnabled)
        if (hooks.isEmpty()) return
        Log.i(tagName, "mirrorEnabled=$mirrorEnabled, ${hooks.size} active hooks")

        var onCreateApplication: XC_MethodHook.Unhook? = null
        onCreateApplication = findMethod(Instrumentation::class.java) {
            name == "callApplicationOnCreate"
            && parameterCount == 1
            && parameterTypes[0] == Application::class.java
        }.hookBefore {
            onCreateApplication?.unhook()
            EzXHelperInit.initAppContext()

            // Detect AA version after app context is available
            val aaVer = try {
                val ctx = InitFields.appContext ?: return@hookBefore
                val vi = ctx.packageManager.getPackageInfo(lpparam.packageName, 0)
                AaVer.parse(vi.versionName)
            } catch (_: Throwable) { null }
            log(tagName, "AA version: ${aaVer?.toString() ?: "unknown"}")
            System.loadLibrary("dexkit")
            DexKitBridge.create(lpparam.appInfo.sourceDir).use { bridge ->
                if (bridge == null) {
                    log(tagName, "DexKitBridge.create() failed")
                    return@hookBefore
                }
                val measureTimeMillis = measureTimeMillis {
                    hooks.forEach { h ->
                        runCatching {
                            h.loadDexClass(bridge, lpparam)
                        }.onFailure { e ->
                            Log.e(tagName, "loadDexClass failed for ${h.tagName}: ${e.message}")
                        }
                    }
                }
                log(tagName, "${lpparam.processName} load class measure ${measureTimeMillis}ms")
            }
            hooks.forEach { h ->
                h.hook(configPreferences, lpparam)
            }
        }
    }
}


