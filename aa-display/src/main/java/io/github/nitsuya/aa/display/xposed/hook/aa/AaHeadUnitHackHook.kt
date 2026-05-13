package io.github.nitsuya.aa.display.xposed.hook.aa

import android.content.ContentResolver
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.util.Log
import com.github.kyuubiran.ezxhelper.utils.findAllMethods
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.nitsuya.aa.display.util.AADisplayConfig
import io.github.nitsuya.aa.display.xposed.hook.AaHook
/**
 * Hook to spoof head unit make from "Mobis" to "Google".
 *
 * Kia Soul reports hu_make = "Mobis" which causes AA to hide third-party apps.
 * Head Unit Revived uses "Google" by default which works fine.
 *
 * This hook intercepts where Android Auto reads the head unit make value
 * and replaces "Mobis" with "Google".
 *
 * DEBUG VERSION: Enhanced logging to identify exact access points.
 */
object AaHeadUnitHackHook : AaHook() {
    override val tagName: String = "AAD_AaHeadUnitHackHook"

    // The head unit make value to spoof (from user's Kia)
    private val ACTUAL_HU_MAKE = "Mobis"
    // What to replace it with (what works in Head Unit Revived)
    private val SPOOFED_HU_MAKE = "Google"

    // Track all column names we've seen for head unit data
    private val seenColumns = mutableSetOf<String>()
    private val seenTables = mutableSetOf<String>()

    override fun isSupportProcess(processName: String): Boolean {
        // Run in all processes to catch the hu_make access
        return processMain == processName || processProjection == processName || processCar == processName
    }

    override fun hook(config: SharedPreferences, lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Use Android Log for visibility (XposedBridge logs may not show in adb logcat)
            Log.i(tagName, "=== HOOK STARTED in ${lpparam.processName} ===")
            
            val enableHeadUnitHack = AADisplayConfig.EnableHeadUnitHack.get(config)
            if (!enableHeadUnitHack) {
                Log.i(tagName, "Head unit hack DISABLED in config")
                return
            }

            Log.i(tagName, "=== HEAD UNIT HACK ENABLED (DEBUG VERSION) ===")
            Log.i(tagName, "Process: ${lpparam.processName}")
            Log.i(tagName, "Replacing '$ACTUAL_HU_MAKE' with '$SPOOFED_HU_MAKE'")

            // Hook ALL getString calls to find head unit make
            hookAllGetStringCalls()

            // Hook ALL getBoolean calls
            hookAllGetBooleanCalls()

            // Hook ALL ContentResolver queries  
            hookAllContentResolverQueries()

            // Hook all SQLite operations
            hookAllSQLiteOperations()

            // Hook all Cursor operations
            hookAllCursorOperations()

            // Hook Parcelable operations (could be passing car info)
            hookAllParcelableOperations()

            // NEW: Hook CarInfo class directly - the primary source of hu_make
            hookCarInfoClass(lpparam)

            // Hook PersistableBundle for telemetry
            hookPersistableBundle()

            // Hook ContentValues for database operations
            hookContentValues()

            Log.d(tagName, "=== Head unit debug hooks installed ===")
        } catch (e: Throwable) {
            Log.e(tagName, "Hook FAILED: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Hook ALL getString methods to find where head unit make is accessed
     */
    private fun hookAllGetStringCalls() {
        try {
            findAllMethods(SharedPreferences::class.java) {
                name == "getString" && parameterCount == 2
            }.hookBefore { param ->
                val key = param.args[0] as? String ?: return@hookBefore
                val value = param.result as? String
                
                // Log all string access for debugging
                if (key.contains("head", ignoreCase = true) || 
                    key.contains("hu_", ignoreCase = true) ||
                    key.contains("car", ignoreCase = true) ||
                    key.contains("unit", ignoreCase = true)) {
                    Log.d(tagName, "SP getString('$key') = '$value'")
                }
                
                // Spoof head unit make
                if (key.contains("headunitmake", ignoreCase = true) ||
                    key == "hu_make" || key == "headUnitMake" ||
                    key == "car_hu_make" || key == "head_unit_make") {
                    Log.w(tagName, ">>> SPOOFING SP '$key': '$value' -> '$SPOOFED_HU_MAKE'")
                    param.result = SPOOFED_HU_MAKE
                }
            }
            Log.i(tagName, "All SharedPreferences.getString hooked")
        } catch (e: Throwable) {
            Log.e(tagName, "SP getString hook failed: ${e.message}")
        }
    }

    /**
     * Hook ALL getBoolean methods
     */
    private fun hookAllGetBooleanCalls() {
        try {
            findAllMethods(SharedPreferences::class.java) {
                name == "getBoolean" && parameterCount == 2
            }.hookBefore { param ->
                val key = param.args[0] as? String ?: return@hookBefore
                when (key) {
                    "allow_unknown_sources" -> {
                        Log.d(tagName, ">>> SPOOFING SP allow_unknown_sources -> true")
                        param.result = true
                    }
                    "device_supported" -> {
                        Log.d(tagName, ">>> SPOOFING SP device_supported -> true")
                        param.result = true
                    }
                }
            }
            Log.d(tagName, "All SharedPreferences.getBoolean hooked")
        } catch (e: Throwable) {
            Log.d(tagName, "SP getBoolean hook failed: ${e.message}")
        }
    }

    /**
     * Hook ALL ContentResolver queries
     */
    private fun hookAllContentResolverQueries() {
        try {
            findAllMethods(ContentResolver::class.java) {
                name == "query"
            }.hookBefore { param ->
                val uri = param.args[0] as? Uri ?: return@hookBefore
                val uriStr = uri.toString()
                
                // Log all queries
                if (uriStr.contains("car", ignoreCase = true) ||
                    uriStr.contains("allowed", ignoreCase = true) ||
                    uriStr.contains("gms", ignoreCase = true)) {
                    Log.d(tagName, "CR.query URI: $uriStr")
                }
            }
            
            findAllMethods(ContentResolver::class.java) {
                name == "query"
            }.hookAfter { param ->
                val uri = param.args[0] as? Uri ?: return@hookAfter
                val uriStr = uri.toString()
                
                if (param.result is Cursor) {
                    val cursor = param.result as Cursor
                    
                    // Log column names
                    val columns = (0 until cursor.columnCount).map { cursor.getColumnName(it) }
                    val colStr = columns.joinToString(", ")
                    
                    if (uriStr.contains("car", ignoreCase = true) ||
                        uriStr.contains("allowed", ignoreCase = true)) {
                        Log.d(tagName, "CR.query RESULT: $uriStr | columns: $colStr")
                    }
                    
                    // Check for head unit make column
                    if (containsHeadUnitMakeColumn(cursor)) {
                        Log.d(tagName, ">>> WRAPPING CURSOR for: $uriStr")
                        seenColumns.addAll(columns)
                        param.result = HeadUnitMakeCursorWrapper(cursor, ACTUAL_HU_MAKE, SPOOFED_HU_MAKE, tagName)
                    }
                }
            }
            Log.d(tagName, "All ContentResolver.query hooked")
        } catch (e: Throwable) {
            Log.d(tagName, "ContentResolver hook failed: ${e.message}")
        }
    }

    /**
     * Hook ALL SQLite operations
     */
    private fun hookAllSQLiteOperations() {
        try {
            findAllMethods("android.database.sqlite.SQLiteDatabase") {
                name == "rawQuery" || name == "query"
            }.hookAfter { param ->
                val sql = if (param.args.isNotEmpty()) param.args[0]?.toString() ?: "" else ""
                
                if (sql.contains("SELECT", ignoreCase = true)) {
                    Log.d(tagName, "SQLite query: ${sql.take(200)}")
                    seenTables.add(sql)
                }
                
                if (param.result is Cursor) {
                    val cursor = param.result as Cursor
                    if (containsHeadUnitMakeColumn(cursor)) {
                        Log.d(tagName, ">>> WRAPPING SQLite cursor")
                        param.result = HeadUnitMakeCursorWrapper(cursor, ACTUAL_HU_MAKE, SPOOFED_HU_MAKE, tagName)
                    }
                }
            }
            Log.d(tagName, "All SQLite operations hooked")
        } catch (e: Throwable) {
            Log.d(tagName, "SQLite hook failed: ${e.message}")
        }

        // Hook CursorWindow (low-level storage)
        try {
            findAllMethods("android.database.CursorWindow") {
                name == "getString"
            }.hookBefore { param ->
                val col = param.args[0] as? Int ?: return@hookBefore
                val value = param.result as? String
                if (value == ACTUAL_HU_MAKE) {
                    Log.d(tagName, ">>> SPOOFING CursorWindow.getString col=$col: '$value' -> '$SPOOFED_HU_MAKE'")
                    param.result = SPOOFED_HU_MAKE
                }
            }
        } catch (e: Throwable) {
            // CursorWindow may not be accessible
        }
    }

    /**
     * Hook ALL Cursor operations
     */
    private fun hookAllCursorOperations() {
        try {
            findAllMethods(Cursor::class.java) {
                name == "getString"
            }.hookBefore { param ->
                val columnIndex = param.args[0] as? Int ?: return@hookBefore
                
                // We need to get column name to check
                val cursor = param.thisObject as? Cursor ?: return@hookBefore
                val colName = try { cursor.getColumnName(columnIndex) } catch (e: Throwable) { "?" }
                val value = param.result as? String
                
                if (colName.contains("headunit", ignoreCase = true) ||
                    colName.contains("humake", ignoreCase = true) ||
                    value == ACTUAL_HU_MAKE) {
                    Log.d(tagName, ">>> Cursor.getString($columnIndex='$colName') = '$value'")
                    if (value == ACTUAL_HU_MAKE) {
                        Log.d(tagName, ">>> SPOOFING: '$value' -> '$SPOOFED_HU_MAKE'")
                        param.result = SPOOFED_HU_MAKE
                    }
                }
            }
            Log.d(tagName, "All Cursor.getString hooked")
        } catch (e: Throwable) {
            Log.d(tagName, "Cursor hook failed: ${e.message}")
        }

        // Hook getColumnIndex
        try {
            findAllMethods(Cursor::class.java) {
                name == "getColumnIndex"
            }.hookBefore { param ->
                val name = param.args[0] as? String ?: return@hookBefore
                if (name.contains("headunit", ignoreCase = true)) {
                    Log.d(tagName, ">>> getColumnIndex('$name') called")
                }
            }
        } catch (e: Throwable) {
            // ignore
        }
    }

    /**
     * Hook Parcelable operations for car info
     */
    private fun hookAllParcelableOperations() {
        try {
            findAllMethods("android.os.Parcel") {
                name == "readString"
            }.hookAfter { param ->
                val value = param.result as? String
                if (value == ACTUAL_HU_MAKE) {
                    Log.d(tagName, ">>> SPOOFING Parcel.readString: '$value' -> '$SPOOFED_HU_MAKE'")
                    param.result = SPOOFED_HU_MAKE
                }
            }
        } catch (e: Throwable) {
            // Parcel may not be hookable this way
        }
    }

    /**
     * NEW: Hook CarInfo class directly - the primary source of hu_make
     */
    private fun hookCarInfoClass(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val carInfoClassName = "com.google.android.gms.car.CarInfo"
            Log.d(tagName, "Looking for CarInfo class...")
            
            // Use findMethod with class name
            val constructors = findAllMethods(carInfoClassName, lpparam.classLoader) {
                name == "<init>"
            }
            Log.d(tagName, "Found ${constructors.size} CarInfo constructors")
            
            constructors.forEach { ctor ->
                ctor.hookBefore { param ->
                    Log.d(tagName, "CarInfo constructor called with ${param.args.size} args")
                    // Parameter index 8 is hu_make (confirmed by smali analysis)
                    if (param.args.size > 8) {
                        val arg8 = param.args[8] as? String
                        if (arg8 != null && arg8 != SPOOFED_HU_MAKE) {
                            Log.w(tagName, ">>> SPOOFING CarInfo constructor arg[8] (hu_make): '$arg8' -> '$SPOOFED_HU_MAKE'")
                            param.args[8] = SPOOFED_HU_MAKE
                        }
                    }
                }
            }
            Log.d(tagName, "CarInfo constructor hooks installed")
        } catch (e: Throwable) {
            Log.e(tagName, "CarInfo hook failed: ${e.message}")
        }

        // Also try CarInfoInternal
        try {
            val carInfoInternalClassName = "com.google.android.gms.carsetup.CarInfoInternal"
            val internalCtors = findAllMethods(carInfoInternalClassName, lpparam.classLoader) {
                name == "<init>"
            }
            Log.d(tagName, "Found ${internalCtors.size} CarInfoInternal constructors")
            
            internalCtors.forEach { ctor ->
                ctor.hookBefore { param ->
                    Log.d(tagName, "CarInfoInternal constructor called with ${param.args.size} args")
                }
            }
        } catch (e: Throwable) {
            Log.d(tagName, "CarInfoInternal not found: ${e.message}")
        }
    }

    /**
     * Hook PersistableBundle.putString for telemetry
     */
    private fun hookPersistableBundle() {
        try {
            findAllMethods("android.os.PersistableBundle") {
                name == "putString" && parameterCount == 2
            }.hookBefore { param ->
                val key = param.args[0] as? String
                val value = param.args[1] as? String
                if (key == "hu_make" && value == ACTUAL_HU_MAKE) {
                    Log.w(tagName, ">>> SPOOFING PersistableBundle.putString('hu_make'): '$value' -> '$SPOOFED_HU_MAKE'")
                    param.args[1] = SPOOFED_HU_MAKE
                }
            }
            Log.d(tagName, "PersistableBundle hooks installed")
        } catch (e: Throwable) {
            Log.e(tagName, "PersistableBundle hook failed: ${e.message}")
        }
    }

    /**
     * Hook ContentValues.put for database operations
     */
    private fun hookContentValues() {
        try {
            findAllMethods("android.content.ContentValues") {
                name == "put" && parameterCount == 2 && parameterTypes[1] == Object::class.java
            }.hookBefore { param ->
                val key = param.args[0] as? String
                val value = param.args[1] as? String
                if (key == "headUnitMake" && value == ACTUAL_HU_MAKE) {
                    Log.w(tagName, ">>> SPOOFING ContentValues.put('headUnitMake'): '$value' -> '$SPOOFED_HU_MAKE'")
                    param.args[1] = SPOOFED_HU_MAKE
                }
            }
            Log.d(tagName, "ContentValues hooks installed")
        } catch (e: Throwable) {
            Log.e(tagName, "ContentValues hook failed: ${e.message}")
        }
    }

    /**
     * Legacy hooks kept for compatibility
     */
    private fun hookAllowedCarsDatabase() {
        // Already handled in hookAllSQLiteOperations
    }

    private fun hookContentResolverQueries() {
        // Already handled in hookAllContentResolverQueries
    }

    private fun hookSharedPreferencesCarData() {
        // Already handled in hookAllGetStringCalls
    }

    private fun hookAllowUnknownSources() {
        // Already handled in hookAllGetBooleanCalls
    }

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
            Log.d(tagName, "Phenotype flags hooks installed")
        } catch (e: Throwable) {
            Log.d(tagName, "Phenotype hook failed: ${e.message}")
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
                Log.i(tagName, "Found headUnitMake column at index $i: ${getColumnName(i)}")
                return@lazy i
            }
        }
        -1
    }

    override fun getString(columnIndex: Int): String? {
        val value = delegate.getString(columnIndex)
        if (columnIndex == headUnitMakeIndex && value != null && value != spoofedMake) {
            Log.i(tagName, "Spoofing headUnitMake: '$value' -> '$spoofedMake'")
            return spoofedMake
        }
        return value
    }
}
