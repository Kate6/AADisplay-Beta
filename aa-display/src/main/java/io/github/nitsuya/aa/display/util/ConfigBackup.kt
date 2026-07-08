package io.github.nitsuya.aa.display.util

import android.content.SharedPreferences
import io.github.nitsuya.aa.display.BuildConfig
import org.json.JSONObject

/**
 * Serialises all [AADisplayConfig] entries to/from JSON for backup across updates.
 */
object ConfigBackup {

    private const val KEY_PREFIX = "aad_cfg_v2"

    fun backup(prefs: SharedPreferences): String {
        val root = JSONObject()
        for (cfg in AADisplayConfig.all) {
            cfg.backupValue(prefs)?.let { raw ->
                root.put(cfg.key, raw)
            }
        }
        root.put("_version", BuildConfig.VERSION_CODE)
        return root.toString()
    }

    fun restore(prefs: SharedPreferences, json: String): Boolean {
        return try {
            val root = JSONObject(json)
            val editor = prefs.edit()
            for (cfg in AADisplayConfig.all) {
                if (root.has(cfg.key)) {
                    cfg.restore(editor, root.getString(cfg.key))
                }
            }
            editor.apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun needsRestore(prefs: SharedPreferences): Boolean {
        val storedVer = AADisplayConfig.ConfigVersion.get(prefs)
        return storedVer < BuildConfig.VERSION_CODE && storedVer != 0
    }
}
