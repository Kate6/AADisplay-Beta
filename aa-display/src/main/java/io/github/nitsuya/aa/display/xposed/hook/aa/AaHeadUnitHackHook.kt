package io.github.nitsuya.aa.display.xposed.hook.aa

import android.content.ContentResolver
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import com.github.kyuubiran.ezxhelper.utils.findAllMethods
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.nitsuya.aa.display.util.AADisplayConfig
import io.github.nitsuya.aa.display.xposed.hook.AaHook
import io.github.nitsuya.aa.display.xposed.log

/**
 * Hook to spoof head unit make from "Mobis" to "Google".
 *
 * Kia Soul reports hu_make = "Mobis" which causes AA to hide third-party apps.
 * Head Unit Revived uses "Google" by default which works fine.
 *
 * This hook intercepts where Android Auto reads the head unit make value
 * and replaces "Mobis" with "Google".
 */
object AaHeadUnitHackHook : AaHook() {
    override val tagName: String = "AAD_AaHeadUnitHackHook"

    // The head unit make value to spoof (from user's Kia)
    private val ACTUAL_HU_MAKE = "Mobis"
    // What to replace it with (what works in Head Unit Revived)
    private val SPOOFED_HU_MAKE = "Google"

    override fun isSupportProcess(processName: String): Boolean {
        // Run in all processes to catch the hu_make access
        return processMain == processName || processProjection == processName || processCar == processName
    }

    override fun hook(config: SharedPreferences, lpparam: XC_LoadPackage.LoadPackageParam) {
        val enableHeadUnitHack = AADisplayConfig.EnableHeadUnitHack.get(config)
        if (!enableHeadUnitHack) {
            log(tagName, "Head unit hack DISABLED")
            return
        }

        // Get custom values from config (defaults work for Kia Soul)
        val huMake = AADisplayConfig.HeadUnitMake.get(config) ?: ACTUAL_HU_MAKE
        val spoofMake = SPOOFED_HU_MAKE

        log(tagName, "=== HEAD UNIT HACK: Replacing '$huMake' with '$spoofMake' ===")

        // Hook 1: SQLite database queries for allowedcars table
        hookAllowedCarsDatabase()

        // Hook 2: ContentResolver queries for car data
        hookContentResolverQueries()

        // Hook 3: SharedPreferences for car data
        hookSharedPreferencesCarData()

        // Hook 4: SharedPreferences - allow unknown sources
        hookAllowUnknownSources()

        // Hook 5: Phenotype flags for compatibility
        hookPhenotypeFlags()

        log(tagName, "=== Head unit hack installed ===")
    }

    /**
     * Hook SQLite queries for the allowedcars table and modify headUnitMake
     */
    private fun hookAllowedCarsDatabase() {
        try {
            // Hook SQLiteDatabase.rawQuery
            findAllMethods("android.database.sqlite.SQLiteDatabase") {
                name == "rawQuery" || name == "query"
            }.hookAfter { param ->
                val sql = if (param.args.isNotEmpty()) param.args[0]?.toString() ?: "" else ""
                if (sql.contains("allowedcars") || sql.contains("headUnitMake") || sql.contains("hu_make")) {
                    log(tagName, "DB query for car data: ${sql.take(100)}")
                }

                if (param.result is Cursor) {
                    val cursor = param.result as Cursor
                    if (containsHeadUnitMakeColumn(cursor)) {
                        log(tagName, "Hooking cursor with headUnitMake column")
                        param.result = HeadUnitMakeCursorWrapper(cursor, ACTUAL_HU_MAKE, SPOOFED_HU_MAKE, tagName)
                    }
                }
            }
            log(tagName, "AllowedCars DB hook installed")
        } catch (e: Throwable) {
            log(tagName, "AllowedCars DB hook failed: ${e.message}")
        }

        // Hook SQLiteStatement
        try {
            findMethod("android.database.sqlite.SQLiteStatement") {
                name == "execute" || name == "executeForString"
            }.hookBefore { param ->
                log(tagName, "SQLiteStatement: ${param.thisObject}")
            }
        } catch (e: Throwable) {
            // Not found
        }
    }

    /**
     * Hook ContentResolver queries for car data providers
     */
    private fun hookContentResolverQueries() {
        try {
            findAllMethods(ContentResolver::class.java) {
                name == "query" && parameterTypes[0] == Uri::class.java
            }.hookBefore { param ->
                val uri = param.args[0] as Uri? ?: return@hookBefore
                val uriStr = uri.toString()

                // Target queries for car service data
                if (uriStr.contains("allowedcars") ||
                    uriStr.contains("carservicedata") ||
                    uriStr.contains("car") && uriStr.contains("data")) {
                    log(tagName, "Car data ContentResolver query: $uriStr")
                }
            }

            findAllMethods(ContentResolver::class.java) {
                name == "query" && parameterTypes[0] == Uri::class.java
            }.hookAfter { param ->
                val uri = param.args[0] as Uri? ?: return@hookAfter
                val uriStr = uri.toString()

                if (uriStr.contains("allowedcars") ||
                    uriStr.contains("carservice") ||
                    uriStr.contains("carinfo")) {

                    if (param.result is Cursor) {
                        val cursor = param.result as Cursor
                        if (containsHeadUnitMakeColumn(cursor)) {
                            log(tagName, "Wrapping cursor for: $uriStr")
                            param.result = HeadUnitMakeCursorWrapper(cursor, ACTUAL_HU_MAKE, SPOOFED_HU_MAKE, tagName)
                        }
                    }
                }
            }
            log(tagName, "ContentResolver hooks installed")
        } catch (e: Throwable) {
            log(tagName, "ContentResolver hook failed: ${e.message}")
        }
    }

    /**
     * Hook SharedPreferences reads for car data
     */
    private fun hookSharedPreferencesCarData() {
        try {
            findAllMethods(SharedPreferences::class.java) {
                name == "getString" && parameterCount == 2
            }.hookBefore { param ->
                val key = param.args[0] as String?
                if (key == "headUnitMake" || key == "hu_make" || key == "car_hu_make") {
                    log(tagName, "SP: $key read, returning '$SPOOFED_HU_MAKE' instead of '$ACTUAL_HU_MAKE'")
                    param.result = SPOOFED_HU_MAKE
                }
            }
            log(tagName, "SharedPreferences car data hooks installed")
        } catch (e: Throwable) {
            log(tagName, "SharedPreferences hook failed: ${e.message}")
        }
    }

    /**
     * Hook allow_unknown_sources SharedPreferences
     */
    private fun hookAllowUnknownSources() {
        try {
            findAllMethods(SharedPreferences::class.java) {
                name == "getBoolean" && parameterCount == 2
            }.hookBefore { param ->
                val key = param.args[0] as String
                when (key) {
                    "allow_unknown_sources" -> {
                        log(tagName, "SP: allow_unknown_sources -> true")
                        param.result = true
                    }
                    "device_supported" -> {
                        log(tagName, "SP: device_supported -> true")
                        param.result = true
                    }
                }
            }
            log(tagName, "Allow unknown sources hook installed")
        } catch (e: Throwable) {
            log(tagName, "Allow unknown sources hook failed: ${e.message}")
        }
    }

    /**
     * Inject phenotype flags for compatibility
     */
    private fun hookPhenotypeFlags() {
        try {
            val flags = listOf(
                "ProjectedAppsFeature__enabled" to "true",
                "RemoteCarApps__enabled" to "true",
                "Watevra__car_hardware_headunit_filter_list" to "",
                "CarActivityManager__gmm_start_sub_phase_timers_hu_make_allow_list" to
                    "Mobis,Google,Pioneer,Kenwood,JVC,Sony,Alpine,Clarion,Parrot",
                "AppValidation__allowed_package_list" to "",
                "AppValidation__blocked_packages_by_installer" to "",
                "AppValidation__dhu_bypass_validation" to "true"
            )

            val cursor = MatrixCursor(arrayOf("key", "value"), flags.size).apply {
                flags.forEach { (k, v) -> addRow(arrayOf(k, v)) }
            }

            findAllMethods(ContentResolver::class.java) {
                name == "query"
            }.hookAfter { param ->
                val uri = param.args[0] as Uri? ?: return@hookAfter
                val uriStr = uri.toString()

                if (uriStr.contains("phenotype") || uriStr.contains("gms.car")) {
                    if (param.result is Cursor) {
                        param.result = android.database.MergeCursor(arrayOf(param.result as Cursor, cursor))
                    }
                }
            }
            log(tagName, "Phenotype flags hooks installed")
        } catch (e: Throwable) {
            log(tagName, "Phenotype hook failed: ${e.message}")
        }
    }

    /**
     * Check if a cursor has a headUnitMake column
     */
    private fun containsHeadUnitMakeColumn(cursor: Cursor): Boolean {
        for (i in 0 until cursor.columnCount) {
            val colName = cursor.getColumnName(i).lowercase()
            if (colName.contains("headunitmake") ||
                colName.contains("head_unit_make") ||
                colName.contains("humake") ||
                colName == "headunitmake") {
                return true
            }
        }
        return false
    }

    /**
     * Find the index of headUnitMake column
     */
    private fun findHeadUnitMakeColumnIndex(cursor: Cursor): Int {
        for (i in 0 until cursor.columnCount) {
            val colName = cursor.getColumnName(i).lowercase()
            if (colName.contains("headunitmake") ||
                colName.contains("head_unit_make") ||
                colName.contains("humake")) {
                return i
            }
        }
        return -1
    }
}

/**
 * Cursor wrapper that replaces headUnitMake values
 */
class HeadUnitMakeCursorWrapper(
    private val delegate: Cursor,
    private val actualMake: String,
    private val spoofedMake: String,
    private val tagName: String
) : Cursor by delegate {

    private val headUnitMakeIndex: Int by lazy {
        for (i in 0 until columnCount) {
            val colName = getColumnName(i).lowercase()
            if (colName.contains("headunitmake") ||
                colName.contains("head_unit_make") ||
                colName.contains("humake")) {
                io.github.nitsuya.aa.display.xposed.log(tagName, "Found headUnitMake column at index $i: ${getColumnName(i)}")
                return@lazy i
            }
        }
        -1
    }

    override fun getString(columnIndex: Int): String? {
        val value = delegate.getString(columnIndex)
        if (columnIndex == headUnitMakeIndex && value == actualMake) {
            io.github.nitsuya.aa.display.xposed.log(tagName, "Spoofing headUnitMake: '$actualMake' -> '$spoofedMake'")
            return spoofedMake
        }
        return value
    }
}
