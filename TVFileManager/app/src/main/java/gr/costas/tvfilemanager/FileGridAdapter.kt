package gr.costas.tvfilemanager

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import java.util.Locale

class FileGridAdapter(
    context: Context
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
    private val items = mutableListOf<FileEntry>()

    fun submitList(newItems: List<FileEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun itemAt(position: Int): FileEntry? = items.getOrNull(position)

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): FileEntry = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_file, parent, false)
        val item = items[position]

        val typeText = view.findViewById<TextView>(R.id.typeText)
        val nameText = view.findViewById<TextView>(R.id.nameText)
        val metaText = view.findViewById<TextView>(R.id.metaText)

        typeText.text = if (item.isDirectory) "ΦΑΚΕΛΟΣ" else extensionLabel(item.name)
        nameText.text = item.name
        metaText.text = if (item.isDirectory) "Άνοιγμα με OK" else humanSize(item.size)
        view.contentDescription = if (item.isDirectory) {
            "Φάκελος ${item.name}"
        } else {
            "Αρχείο ${item.name}, ${humanSize(item.size)}"
        }
        return view
    }

    private fun extensionLabel(name: String): String {
        val ext = name.substringAfterLast('.', "ΑΡΧΕΙΟ")
        return ext.uppercase(Locale.getDefault()).take(8)
    }

    private fun humanSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.getDefault(), "%.2f GB", gb)
    }
}
