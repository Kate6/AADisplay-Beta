package io.github.nitsuya.aa.display.ui.setting

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.nitsuya.aa.display.util.LogBuffer

class LogAdapter(initial: List<LogBuffer.Entry>) :
    ListAdapter<LogBuffer.Entry, LogAdapter.ViewHolder>(DiffCallback) {

    init { submitList(initial) }

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val tv = TextView(parent.context).apply {
            textSize = 11f
            setPadding(12, 4, 12, 4)
            isSingleLine = false
            maxLines = 20
        }
        return ViewHolder(tv)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = getItem(position).display
    }

    fun update(entries: List<LogBuffer.Entry>) {
        submitList(entries)
    }

    private object DiffCallback : DiffUtil.ItemCallback<LogBuffer.Entry>() {
        override fun areItemsTheSame(a: LogBuffer.Entry, b: LogBuffer.Entry) = a.timestamp == b.timestamp
        override fun areContentsTheSame(a: LogBuffer.Entry, b: LogBuffer.Entry) = a == b
    }
}
