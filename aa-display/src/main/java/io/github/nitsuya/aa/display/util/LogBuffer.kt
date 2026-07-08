package io.github.nitsuya.aa.display.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * In-memory ring-buffer log for in-app log viewer.
 * Thread-safe for concurrent writes from Xposed hooks.
 */
object LogBuffer {

    data class Entry(
        val timestamp: Long,
        val level: Char,   // V, D, I, W, E
        val tag: String,
        val message: String,
    ) {
        val formattedTime: String get() = dateFmt.format(Date(timestamp))
        val display: String get() = "$formattedTime $level/$tag: $message"
    }

    private const val MAX_ENTRIES = 2048
    private val entries = ArrayDeque<Entry>(MAX_ENTRIES)
    private val lock = Any()

    private val dateFmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun write(level: Char, tag: String, message: String) {
        synchronized(lock) {
            if (entries.size >= MAX_ENTRIES) entries.removeFirst()
            entries.addLast(Entry(System.currentTimeMillis(), level, tag, message))
        }
    }

    fun v(tag: String, msg: String) { write('V', tag, msg); Log.v(tag, msg) }
    fun d(tag: String, msg: String) { write('D', tag, msg); Log.d(tag, msg) }
    fun i(tag: String, msg: String) { write('I', tag, msg); Log.i(tag, msg) }
    fun w(tag: String, msg: String) { write('W', tag, msg); Log.w(tag, msg) }
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        write('E', tag, "$msg ${tr?.message ?: ""}")
        if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
    }

    fun snapshot(): List<Entry> = synchronized(lock) { entries.toList() }

    fun search(query: String): List<Entry> = synchronized(lock) {
        val q = query.lowercase()
        entries.filter { it.tag.lowercase().contains(q) || it.message.lowercase().contains(q) }
    }

    fun clear() = synchronized(lock) { entries.clear() }
}
