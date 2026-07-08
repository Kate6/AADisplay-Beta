package io.github.nitsuya.aa.display.ui.setting

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.nitsuya.aa.display.R
import io.github.nitsuya.aa.display.util.LogBuffer

class LogViewerDialog(private val activity: Activity) {

    fun show() {
        val entries = LogBuffer.snapshot()
        val sb = StringBuilder()
        for (e in entries) {
            sb.appendLine(e.display)
        }
        val textView = TextView(activity).apply {
            text = if (sb.isEmpty()) "No logs yet" else sb.toString()
            textSize = 11f
            setPadding(24, 16, 24, 16)
            setLineSpacing(0f, 1.1f)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val scrollView = android.widget.ScrollView(activity).apply {
            addView(textView)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isVerticalScrollBarEnabled = true
        }

        MaterialAlertDialogBuilder(activity)
            .setTitle("Log Viewer (${entries.size} entries)")
            .setView(scrollView)
            .setPositiveButton("Copy") { _, _ ->
                val clip = ClipData.newPlainText("AADisplay Log", textView.text)
                (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(clip)
                Toast.makeText(activity, "Log copied", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Clear") { _, _ ->
                LogBuffer.clear()
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
