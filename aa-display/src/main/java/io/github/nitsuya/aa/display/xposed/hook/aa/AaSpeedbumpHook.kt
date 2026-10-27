package io.github.nitsuya.aa.display.xposed.hook.aa

import android.content.SharedPreferences
import android.view.View
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import com.github.kyuubiran.ezxhelper.utils.loadClass
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.nitsuya.aa.display.util.AADisplayConfig
import io.github.nitsuya.aa.display.xposed.hook.AaHook
import io.github.nitsuya.aa.display.xposed.hook.abortMethod
import io.github.nitsuya.aa.display.xposed.log
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Method

/**
 * Disables Android Auto's periodic "speedbump" touchscreen lockout
 * ("Safety pause. Back soon.").
 *
 * AA implements this in three places, all of them obfuscated per version:
 *
 *  1. Interaction moderator (moz / "GH.InteractionModerator"), subclassed by the
 *     SixTap legacy moderator (mpa / "GH.SixTap") and the token-bucket moderator
 *     (mpc / "GH.SpeedBump"). Every user action enters via the final method
 *     moz.k(gxx, zoo) and the lockout state is broadcast via moz.j(moy).
 *     Aborting k() means no action is ever processed -> permits never drain ->
 *     state stays MODERATED/UNLIMITED -> the overlay is never shown.
 *
 *  2. App-host SpeedbumpService decision (gmg), which combines a "content
 *     moderation enabled" flag with an event and returns whether to trigger.
 *     Forcing that to false kills the app-host (Car App Library) path.
 *
 *  3. ContentRateLimiter token bucket (mow / "GH.ContentRateLimiter"), whose
 *     permit getter is forced high so the <=1.0 trigger can never be reached.
 *
 * All three are resolved by DexKit string anchors so they survive class renames.
 * A UI-level SpeedbumpView guard is installed as a last-resort fallback.
 */
object AaSpeedbumpHook : AaHook() {
    override val tagName: String = "AAD_AaSpeedbumpHook"

    // Stable string constants found in the dex; class names are obfuscated.
    private const val ANCHOR_INTERACTION_MODERATOR = "GH.InteractionModerator"
    private const val ANCHOR_SPEEDBUMP_SERVICE = "Speedbump triggered from SpeedbumpService."
    private const val ANCHOR_CONTENT_RATE_LIMITER = "GH.ContentRateLimiter"

    // Not obfuscated (library class name survives shrinking).
    private const val SPEEDBUMP_VIEW = "com.google.android.apps.auto.components.ui.speedbump.SpeedbumpView"

    private var interactionModeratorClass: String? = null
    private var speedbumpDecisionClass: String? = null
    private var contentRateLimiterClass: String? = null

    override fun isSupportProcess(processName: String): Boolean {
        return processMain == processName || processProjection == processName || processCar == processName
    }

    // The speedbump lockout is AA's own UI behaviour, not part of mirroring;
    // keep it disabled even when the mirror is off.
    override val runsWhenMirrorDisabled: Boolean = true

    override fun loadDexClass(bridge: DexKitBridge, lpparam: XC_LoadPackage.LoadPackageParam) {
        interactionModeratorClass = findClassByString(bridge, ANCHOR_INTERACTION_MODERATOR)
        speedbumpDecisionClass = findClassByString(bridge, ANCHOR_SPEEDBUMP_SERVICE)
        contentRateLimiterClass = findClassByString(bridge, ANCHOR_CONTENT_RATE_LIMITER)
        log(
            tagName,
            "resolved classes: moderator=$interactionModeratorClass " +
                "decision=$speedbumpDecisionClass rateLimiter=$contentRateLimiterClass"
        )
    }

    private fun findClassByString(bridge: DexKitBridge, anchor: String): String? {
        return runCatching {
            val classes = bridge.findClass {
                searchPackages = listOf("")
                matcher {
                    usingStrings {
                        add(anchor, StringMatchType.Equals, false)
                    }
                }
            }
            when {
                classes.isEmpty() -> {
                    log(tagName, "anchor not found: $anchor")
                    null
                }
                else -> classes[0].name
            }
        }.onFailure { e ->
            log(tagName, "anchor search failed: $anchor", e)
        }.getOrNull()
    }

    override fun hook(config: SharedPreferences, lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!AADisplayConfig.DisableSpeedbump.get(config)) {
            log(tagName, "DisableSpeedbump disabled in config")
            return
        }
        log(tagName, "installing speedbump kill switches in ${lpparam.processName}")

        runCatching { hookInteractionModerator(lpparam) }
            .onFailure { log(tagName, "strategy 1 (interaction moderator) failed", it) }
        runCatching { hookSpeedbumpDecision(lpparam) }
            .onFailure { log(tagName, "strategy 2 (speedbump service decision) failed", it) }
        runCatching { hookContentRateLimiter(lpparam) }
            .onFailure { log(tagName, "strategy 3 (content rate limiter) failed", it) }
        runCatching { hookSpeedbumpViewFallback(lpparam) }
            .onFailure { log(tagName, "strategy 4 (SpeedbumpView fallback) failed", it) }
    }

    /**
     * Strategy 1: abort moz.k(gxx, zoo), the single final entry point through which
     * every subclassed moderator processes user actions. Nothing enters, so no
     * permit is ever spent and the LOCKED state can never be reached.
     */
    private fun hookInteractionModerator(lpparam: XC_LoadPackage.LoadPackageParam) {
        val className = interactionModeratorClass ?: run {
            log(tagName, "strategy 1 skipped: moderator class not resolved")
            return
        }
        val clazz = loadClass(className, lpparam.classLoader)
        val candidates = clazz.declaredMethods.filter { m ->
            m.name == "k" && m.parameterCount == 2 && java.lang.reflect.Modifier.isFinal(m.modifiers)
        }
        if (candidates.isEmpty()) {
            log(tagName, "strategy 1 skipped: no final 2-arg k() in $className")
            return
        }
        candidates.forEach { m ->
            m.hookBefore { param -> param.abortMethod() }
        }
        log(tagName, "strategy 1 engaged: aborted ${className}.k(${candidates.size} overloads)")
    }

    /**
     * Strategy 2: force the app-host SpeedbumpService decision to "do not trigger".
     * The decision type implements a lambda bridge whose body returns a Boolean.
     */
    private fun hookSpeedbumpDecision(lpparam: XC_LoadPackage.LoadPackageParam) {
        val className = speedbumpDecisionClass ?: run {
            log(tagName, "strategy 2 skipped: decision class not resolved")
            return
        }
        val clazz = loadClass(className, lpparam.classLoader)
        val targets: List<Method> = clazz.declaredMethods.filter { m ->
            m.name == "invoke" || (m.parameterCount == 1 && m.returnType == Any::class.java)
        }
        if (targets.isEmpty()) {
            log(tagName, "strategy 2 skipped: no decision methods in $className")
            return
        }
        targets.forEach { m ->
            m.hookBefore { param -> param.result = java.lang.Boolean.FALSE }
        }
        log(tagName, "strategy 2 engaged: forced false on ${targets.map { it.name }} in $className")
    }

    /**
     * Strategy 3: make the token bucket report a huge number of available permits.
     * All float-returning no-arg methods (permit getters) are pinned, so the
     * "permits <= 1.0f => lockout" branch is unreachable.
     */
    private fun hookContentRateLimiter(lpparam: XC_LoadPackage.LoadPackageParam) {
        val className = contentRateLimiterClass ?: run {
            log(tagName, "strategy 3 skipped: rate limiter class not resolved")
            return
        }
        val clazz = loadClass(className, lpparam.classLoader)
        val getters = clazz.declaredMethods.filter { m ->
            m.parameterCount == 0 && (m.returnType == Float::class.javaPrimitiveType)
        }
        if (getters.isEmpty()) {
            log(tagName, "strategy 3 skipped: no float getters in $className")
            return
        }
        getters.forEach { m ->
            m.hookBefore { param -> param.result = 1.0E9f }
        }
        log(tagName, "strategy 3 engaged: pinned ${getters.map { it.name }} in $className")
    }

    /**
     * Strategy 4: last resort. If the overlay is ever asked to become visible,
     * keep it GONE so it can never swallow touch input.
     */
    private fun hookSpeedbumpViewFallback(lpparam: XC_LoadPackage.LoadPackageParam) {
        val clazz = loadClass(SPEEDBUMP_VIEW, lpparam.classLoader)
        val setVisibility = findMethod(View::class.java) {
            name == "setVisibility" && parameterCount == 1
        }
        setVisibility.hookBefore { param ->
            if (clazz.isInstance(param.thisObject) && (param.args[0] as? Int) == View.VISIBLE) {
                param.args[0] = View.GONE
            }
        }
        log(tagName, "strategy 4 engaged: SpeedbumpView visibility guard installed")
    }
}
