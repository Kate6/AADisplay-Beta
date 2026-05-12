package io.github.nitsuya.aa.display.xposed.hook.aa

import android.content.ContentResolver
import android.content.ContentValues
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.database.MergeCursor
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
 * Hook to bypass head unit compatibility checks and allow third-party apps.
 * Kia Soul: Mobis, standard_m_5, KIA_US
 *
 * Key insight: The head unit info comes from the car's handshake and is stored in
 * carservicedata.db on the phone. The filtering happens in gearhead based on
 * this info combined with phenotype flags and SharedPreferences.
 */
object AaHeadUnitHackHook : AaHook() {
    override val tagName: String = "AAD_AaHeadUnitHackHook"

    override fun isSupportProcess(processName: String): Boolean {
        return processMain == processName || processProjection == processName || processCar == processName
    }

    override fun hook(config: SharedPreferences, lpparam: XC_LoadPackage.LoadPackageParam) {
        val enableHeadUnitHack = AADisplayConfig.EnableHeadUnitHack.get(config)
        if (!enableHeadUnitHack) {
            log(tagName, "Head unit hack DISABLED")
            return
        }

        log(tagName, "=== HEAD UNIT HACK ACTIVATED for ${lpparam.processName} ===")

        // Hook 1: SharedPreferences - force permissive values
        hookSharedPreferencesBypass()

        // Hook 2: Phenotype queries
        hookPhenotypeBypass()

        // Hook 3: Car database queries (carservicedata.db)
        hookCarDatabaseQueries()

        // Hook 4: App filtering methods
        hookAppFiltering()

        // Hook 5: Force ProjectedAppsFeature
        hookProjectedAppsFeature()

        // Hook 6: ContentProvider queries for car data
        hookContentProviderQueries()

        log(tagName, "=== Head unit hack installed ===")
    }

    private fun hookSharedPreferencesBypass() {
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
                    "developer_settings_enabled" -> {
                        log(tagName, "SP: developer_settings_enabled -> true")
                        param.result = true
                    }
                }
            }
            log(tagName, "SP getBoolean hooks installed")
        } catch (e: Throwable) {
            log(tagName, "SP getBoolean hook failed: ${e.message}")
        }

        try {
            findAllMethods(SharedPreferences::class.java) {
                name == "getString" && parameterCount == 2
            }.hookBefore { param ->
                val key = param.args[0] as String
                if (key.contains("allowed_package") || key.contains("blocked_package")) {
                    log(tagName, "SP: Clearing restriction for $key")
                    param.result = ""
                }
            }
            log(tagName, "SP getString hooks installed")
        } catch (e: Throwable) {
            log(tagName, "SP getString hook failed: ${e.message}")
        }
    }

    private fun hookPhenotypeBypass() {
        try {
            val flags = listOf(
                // Enable features
                "ProjectedAppsFeature__enabled" to "true",
                "RemoteCarApps__enabled" to "true",
                "COOLWALK_UI_ENABLED" to "true",

                // Clear head unit filters
                "Watevra__car_hardware_headunit_filter_list" to "",
                "CarActivityManager__gmm_start_sub_phase_timers_hu_make_allow_list" to
                    "Mobis,Google,Pioneer,Kenwood,JVC,Sony,Alpine,Clarion,Parrot",

                // Clear validation restrictions
                "AppValidation__allowed_package_list" to "",
                "AppValidation__blocked_packages_by_installer" to "",
                "AppValidation__dhu_bypass_validation" to "true",

                // Car projector settings
                "CarProjectionValidator__include_enabled_only_components" to "false",
                "CarProjectionValidator__filter_disabled_packages_in_ispackageallowed_method" to "false"
            )

            val cursor = MatrixCursor(arrayOf("key", "value"), flags.size).apply {
                flags.forEach { (k, v) -> addRow(arrayOf(k, v)) }
            }

            findAllMethods(ContentResolver::class.java) {
                name == "query" && parameterTypes[0] == Uri::class.java
            }.hookAfter { param ->
                val uri = param.args[0] as Uri? ?: return@hookAfter
                val uriStr = uri.toString()

                if (uriStr.contains("phenotype") || uriStr.contains("gms.car") || uriStr.contains("gearhead")) {
                    log(tagName, "Phenotype query: $uriStr")

                    if (param.result is Cursor) {
                        param.result = MergeCursor(arrayOf(param.result as Cursor, cursor))
                    }
                }
            }
            log(tagName, "Phenotype hooks installed (${flags.size} flags)")
        } catch (e: Throwable) {
            log(tagName, "Phenotype hooks failed: ${e.message}")
        }
    }

    private fun hookCarDatabaseQueries() {
        // Hook SQLite database queries for carservicedata.db
        try {
            findMethod("android.database.sqlite.SQLiteDatabase") {
                name == "query" || name == "rawQuery"
            }.hookAfter { param ->
                val methodName = param.method.name
                val arg0 = if (param.args.isNotEmpty()) param.args[0] else null
                val arg1 = if (param.args.size > 1) param.args[1] else null

                val table = when {
                    arg0 is String -> arg0 as String
                    arg1 is String -> arg1 as String
                    else -> ""
                }

                if (table.contains("allowedcars") || table.contains("carservice")) {
                    log(tagName, "Car DB query: $table via $methodName")
                }
            }
            log(tagName, "Car database hooks installed")
        } catch (e: Throwable) {
            log(tagName, "Car DB hooks failed: ${e.message}")
        }

        // Hook ContentResolver for car data provider
        try {
            findMethod(ContentResolver::class.java) {
                name == "query" && parameterTypes[0] == Uri::class.java
            }.hookBefore { param ->
                val uri = param.args[0] as Uri? ?: return@hookBefore
                val uriStr = uri.toString()

                // Target car data queries
                if (uriStr.contains("carservice") ||
                    uriStr.contains("allowedcars") ||
                    uriStr.contains("carinfo") ||
                    uriStr.contains("headunit")) {
                    log(tagName, "Car data query intercepted: $uriStr")
                }
            }
        } catch (e: Throwable) {
            // Hook failed
        }
    }

    private fun hookAppFiltering() {
        // Hook PackageManager queries
        try {
            findMethod("android.content.pm.PackageManager") {
                name == "getInstalledPackages" ||
                name == "queryIntentServices"
            }.hookAfter { param ->
                log(tagName, "PM method: ${param.method.name}")
            }
        } catch (e: Throwable) {
            // PM hook failed
        }

        // Hook gearhead validation classes
        try {
            findMethod("com.google.android.projection.gearhead.validation") {
                name.contains("is") && name.contains("Allowed")
            }.hookBefore { param ->
                log(tagName, "Bypassing validation: ${param.method.name}")
                param.result = true
            }
        } catch (e: Throwable) {
            // Class not found
        }

        // Hook CarProjectionValidator
        try {
            findMethod("com.google.android.projection.gearhead") {
                name.contains("CarProjectionValidator") ||
                (name.contains("isPackage") && name.contains("Allowed"))
            }.hookBefore { param ->
                log(tagName, "CarProjectionValidator: ${param.method.name} -> true")
                param.result = true
            }
        } catch (e: Throwable) {
            // Not found
        }

        log(tagName, "App filtering hooks attempted")
    }

    private fun hookProjectedAppsFeature() {
        // Hook the feature gate class
        try {
            findMethod("com.google.android.projection.gearhead") {
                name.contains("ProjectedApps") ||
                name.contains("FeatureGate") ||
                name.contains("Feature")
            }.hookAfter { param ->
                log(tagName, "Feature gate: ${param.method.name} -> ${param.result}")
            }
        } catch (e: Throwable) {
            // Not found
        }

        // Hook any boolean return type methods that might control visibility
        try {
            findMethod("com.google.android.projection.gearhead") {
                name.startsWith("is") || name.startsWith("can") || name.startsWith("should")
            }.hookBefore { param ->
                val result = param.result
                if (result is Boolean && !result) {
                    log(tagName, "Enabling: ${param.method.name}")
                    param.result = true
                }
            }
        } catch (e: Throwable) {
            // Not found
        }

        log(tagName, "ProjectedApps feature hooks attempted")
    }

    private fun hookContentProviderQueries() {
        // Hook ALL ContentResolver queries and log them
        try {
            findAllMethods(ContentResolver::class.java) {
                name == "query"
            }.hookBefore { param ->
                val uri = param.args[0] as Uri? ?: return@hookBefore
                val uriStr = uri.toString()

                // Log all queries from gearhead to see what's being queried
                if (uriStr.contains("projection") ||
                    uriStr.contains("car") ||
                    uriStr.contains("auto") ||
                    uriStr.contains("google")) {
                    log(tagName, "Gearhead query: $uriStr")
                }
            }
        } catch (e: Throwable) {
            log(tagName, "ContentProvider hook failed: ${e.message}")
        }

        // Hook ContentValues to potentially modify data being written
        try {
            findAllMethods("android.content.ContentResolver") {
                name == "insert" || name == "update"
            }.hookBefore { param ->
                val uri = param.args[0] as Uri? ?: return@hookBefore
                val uriStr = uri.toString()

                if (uriStr.contains("carservice") || uriStr.contains("allowedcars")) {
                    log(tagName, "Car data write: $uriStr")
                }
            }
        } catch (e: Throwable) {
            // Hook failed
        }
    }
}
