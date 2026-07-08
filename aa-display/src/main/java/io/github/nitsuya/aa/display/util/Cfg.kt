package io.github.nitsuya.aa.display.util

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor

/**
 * Typed configuration key with store backend selection.
 */
enum class Store {
    /** Regular SharedPreferences */
    DE,
    /** XSharedPreferences (read-only from Xposed) */
    XM,
}

abstract class Cfg<T>(
    val key: String,
    val default: T,
    val file: String = AADisplayConfig.ConfigName,
    val store: Store = Store.DE,
) {
    abstract fun get(prefs: SharedPreferences?): T
    abstract fun put(editor: Editor, value: T): Editor
    abstract fun rawString(prefs: SharedPreferences?): String?
    abstract fun restore(editor: Editor, raw: String): Editor

    fun backupValue(prefs: SharedPreferences?): String? {
        if (prefs?.contains(key) != true) return null
        return rawString(prefs)
    }
}

class StringCfg(
    key: String,
    default: String? = null,
    file: String = AADisplayConfig.ConfigName,
    store: Store = Store.DE,
) : Cfg<String?>(key, default, file, store) {
    override fun get(prefs: SharedPreferences?): String? {
        return prefs?.getString(key, default)?.trim()?.ifBlank { default } ?: default
    }
    override fun put(editor: Editor, value: String?): Editor =
        if (value != null) editor.putString(key, value) else editor.remove(key)
    override fun rawString(prefs: SharedPreferences?): String? = prefs?.getString(key, null)
    override fun restore(editor: Editor, raw: String): Editor = editor.putString(key, raw)
}

class BooleanCfg(
    key: String,
    default: Boolean = false,
    file: String = AADisplayConfig.ConfigName,
    store: Store = Store.DE,
) : Cfg<Boolean>(key, default, file, store) {
    override fun get(prefs: SharedPreferences?): Boolean = prefs?.getBoolean(key, default) ?: default
    override fun put(editor: Editor, value: Boolean): Editor = editor.putBoolean(key, value)
    override fun rawString(prefs: SharedPreferences?): String? =
        if (prefs?.contains(key) == true) prefs.getBoolean(key, default).toString() else null
    override fun restore(editor: Editor, raw: String): Editor = editor.putBoolean(key, raw.toBoolean())
}

class IntCfg(
    key: String,
    default: Int = 0,
    file: String = AADisplayConfig.ConfigName,
    store: Store = Store.DE,
) : Cfg<Int>(key, default, file, store) {
    private val defStr = default.toString()
    override fun get(prefs: SharedPreferences?): Int {
        val s = prefs?.getString(key, defStr) ?: return default
        return s.toIntOrNull() ?: default
    }
    override fun put(editor: Editor, value: Int): Editor = editor.putString(key, value.toString())
    override fun rawString(prefs: SharedPreferences?): String? =
        if (prefs?.contains(key) == true) prefs.getString(key, defStr) else null
    override fun restore(editor: Editor, raw: String): Editor = editor.putString(key, raw)
}

class StringArrayCfg(
    key: String,
    file: String = AADisplayConfig.ConfigName,
    store: Store = Store.DE,
) : Cfg<Array<String>>(key, emptyArray(), file, store) {
    @Suppress("UNCHECKED_CAST")
    override fun get(prefs: SharedPreferences?): Array<String> {
        return prefs?.getStringSet(key, null)?.toTypedArray() ?: emptyArray()
    }
    override fun put(editor: Editor, value: Array<String>): Editor =
        editor.putStringSet(key, value.toSet())
    override fun rawString(prefs: SharedPreferences?): String? =
        prefs?.getStringSet(key, null)?.joinToString("\n")
    override fun restore(editor: Editor, raw: String): Editor =
        editor.putStringSet(key, raw.split("\n").filter { it.isNotBlank() }.toSet())
}

class PropertiesCfg(
    key: String,
    file: String = AADisplayConfig.ConfigName,
    store: Store = Store.DE,
) : Cfg<java.util.Properties?>(key, null, file, store) {
    override fun get(prefs: SharedPreferences?): java.util.Properties? {
        val raw = prefs?.getString(key, null) ?: return null
        return java.util.Properties().apply { load(java.io.StringReader(raw)) }
    }
    override fun put(editor: Editor, value: java.util.Properties?): Editor =
        if (value != null) {
            val sw = java.io.StringWriter()
            value.store(sw, null)
            editor.putString(key, sw.toString())
        } else editor.remove(key)
    override fun rawString(prefs: SharedPreferences?): String? = prefs?.getString(key, null)
    override fun restore(editor: Editor, raw: String): Editor = editor.putString(key, raw)
}
