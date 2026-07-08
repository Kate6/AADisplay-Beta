package io.github.nitsuya.aa.display.xposed.hook.aa

/**
 * Android Auto version parsed from [versionName] (e.g. "16.1.0").
 */
data class AaVer(
    val major: Int,
    val minor: Int = 0,
    val patch: Int = 0,
) : Comparable<AaVer> {
    override fun compareTo(other: AaVer): Int {
        return comparator.compare(this, other)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val comparator = compareBy(AaVer::major, AaVer::minor, AaVer::patch)

        /** Parse "16.1.0" or "16.1" or "16" */
        fun parse(versionName: String?): AaVer? {
            if (versionName.isNullOrBlank()) return null
            val parts = versionName.trim().split(".")
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            return AaVer(major, minor, patch)
        }
    }
}

/**
 * A range of AA versions that a [HookResolver] supports.
 */
data class AaRange(
    val from: AaVer,
    val to: AaVer? = null,   // null = any later version
) {
    operator fun contains(ver: AaVer): Boolean {
        if (ver < from) return false
        if (to != null && ver > to) return false
        return true
    }
}

/**
 * Result of selecting a resolver for a given AA version.
 */
sealed class Selection<out T> {
    /** Exact match within range */
    data class Exact<T>(val resolver: T) : Selection<T>()
    /** Best-effort (no exact match, but compatible) */
    data class BestEffort<T>(val resolver: T) : Selection<T>()
    /** No resolver applicable */
    object None : Selection<Nothing>()
}

/**
 * Resolves DexKit class references and applies hooks for a given AA version range.
 */
interface HookResolver {
    val range: AaRange
    fun resolve(bridge: org.luckypray.dexkit.DexKitBridge, lpparam: de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam) {}
    fun apply(config: android.content.SharedPreferences, lpparam: de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam)
}
