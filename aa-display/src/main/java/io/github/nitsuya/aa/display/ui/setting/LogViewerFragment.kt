package io.github.nitsuya.aa.display.ui.setting

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.duzhaokun123.template.bases.BaseFragment
import io.github.nitsuya.aa.display.R
import io.github.nitsuya.aa.display.databinding.ActivitySettingsBinding
import io.github.nitsuya.aa.display.util.LogBuffer

class LogViewerFragment : BaseFragment<ActivitySettingsBinding>(ActivitySettingsBinding::class.java) {

    private lateinit var adapter: LogAdapter
    private var currentQuery: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)

        adapter = LogAdapter(emptyList())
        baseBinding.flRoot.apply {
            val rv = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
                adapter = this@LogViewerFragment.adapter
            }
            addView(rv)
        }
        refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_log, menu)
        val searchItem = menu.findItem(R.id.action_search)
        (searchItem.actionView as? SearchView)?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentQuery = query ?: ""
                refresh()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                currentQuery = newText ?: ""
                refresh()
                return true
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear -> {
                LogBuffer.clear()
                refresh()
                true
            }
            R.id.action_copy -> {
                val text = LogBuffer.snapshot().joinToString("\n") { it.display }
                val clip = ClipData.newPlainText("AADisplay Log", text)
                (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(clip)
                Toast.makeText(context, "Log copied", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refresh() {
        val entries = if (currentQuery.isBlank()) LogBuffer.snapshot()
        else LogBuffer.search(currentQuery)
        adapter.update(entries)
    }
}
