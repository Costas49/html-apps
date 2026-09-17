package gr.costas.tvfilemanager

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    companion object {
        private const val REQ_OPEN_TREE = 7001
        private const val PREFS = "tv_file_manager"
        private const val PREF_TREE_URI = "tree_uri"
    }

    private lateinit var fileGrid: GridView
    private lateinit var pathText: TextView
    private lateinit var statusText: TextView
    private lateinit var adapter: FileGridAdapter

    private var repository: SafRepository? = null
    private var currentDirectory: Uri? = null
    private val navigationStack = ArrayDeque<Uri>()

    private var allEntries: List<FileEntry> = emptyList()
    private var selectedEntry: FileEntry? = null
    private var sortAscending = true

    private var clipboardUri: Uri? = null
    private var clipboardMove = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fileGrid = findViewById(R.id.fileGrid)
        pathText = findViewById(R.id.pathText)
        statusText = findViewById(R.id.statusText)
        adapter = FileGridAdapter(this)
        fileGrid.adapter = adapter

        wireGrid()
        wireButtons()
        restoreStorage()
    }

    private fun wireGrid() {
        fileGrid.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedEntry = adapter.itemAt(position)
                selectedEntry?.let {
                    statusText.text = if (it.isDirectory) {
                        "Επιλεγμένος φάκελος: ${it.name}"
                    } else {
                        "Επιλεγμένο αρχείο: ${it.name} • ${humanSize(it.size)}"
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedEntry = null
            }
        }

        fileGrid.setOnItemClickListener { _, _, position, _ ->
            adapter.itemAt(position)?.let { openEntry(it) }
        }

        fileGrid.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_MENU) {
                showInfo()
                true
            } else {
                false
            }
        }
    }

    private fun wireButtons() {
        findViewById<Button>(R.id.btnStorage).setOnClickListener { chooseStorage() }
        findViewById<Button>(R.id.btnUp).setOnClickListener { navigateUp() }
        findViewById<Button>(R.id.btnNewFolder).setOnClickListener { newFolder() }
        findViewById<Button>(R.id.btnCopy).setOnClickListener { copySelected(false) }
        findViewById<Button>(R.id.btnMove).setOnClickListener { copySelected(true) }
        findViewById<Button>(R.id.btnPaste).setOnClickListener { pasteClipboard() }
        findViewById<Button>(R.id.btnRename).setOnClickListener { renameSelected() }
        findViewById<Button>(R.id.btnDelete).setOnClickListener { deleteSelected() }
        findViewById<Button>(R.id.btnSearch).setOnClickListener { searchCurrentFolder() }
        findViewById<Button>(R.id.btnSort).setOnClickListener { toggleSort() }
        findViewById<Button>(R.id.btnInfo).setOnClickListener { showInfo() }
    }

    private fun chooseStorage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        }
        try {
            startActivityForResult(intent, REQ_OPEN_TREE)
        } catch (_: ActivityNotFoundException) {
            toast("Η συσκευή δεν διαθέτει συμβατό επιλογέα φακέλων.")
        }
    }

    @Deprecated("Deprecated in Android, retained for API 26+ compatibility without extra dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_OPEN_TREE || resultCode != RESULT_OK) return

        val uri = data?.data ?: return
        val flags = data.flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // Some OEM providers grant access for the current session only.
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(PREF_TREE_URI, uri.toString())
            .apply()

        attachStorage(uri)
    }

    private fun restoreStorage() {
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_TREE_URI, null)
        if (saved.isNullOrBlank()) {
            adapter.submitList(emptyList())
            statusText.text = "Πάτησε «Αποθήκευση» και επίλεξε εσωτερικό χώρο ή USB."
            findViewById<Button>(R.id.btnStorage).requestFocus()
            return
        }

        try {
            attachStorage(Uri.parse(saved))
        } catch (_: Exception) {
            statusText.text = "Η προηγούμενη πρόσβαση έληξε. Επίλεξε ξανά χώρο αποθήκευσης."
            findViewById<Button>(R.id.btnStorage).requestFocus()
        }
    }

    private fun attachStorage(treeUri: Uri) {
        val repo = SafRepository(this, treeUri)
        repository = repo
        navigationStack.clear()
        currentDirectory = repo.rootDocumentUri()
        loadDirectory(requestFocus = true)
    }

    private fun loadDirectory(requestFocus: Boolean = false) {
        val repo = repository ?: return
        val dir = currentDirectory ?: return

        try {
            allEntries = repo.listChildren(dir)
            val sorted = sortEntries(allEntries)
            adapter.submitList(sorted)
            selectedEntry = sorted.firstOrNull()

            val dirName = repo.getEntry(dir)?.name ?: friendlyDocumentId(dir)
            pathText.text = "Φάκελος: $dirName"
            statusText.text = "${sorted.size} στοιχεία • OK για άνοιγμα • BACK για επιστροφή"

            if (requestFocus && sorted.isNotEmpty()) {
                fileGrid.post {
                    fileGrid.setSelection(0)
                    fileGrid.requestFocus()
                }
            }
        } catch (e: Exception) {
            adapter.submitList(emptyList())
            statusText.text = "Δεν ήταν δυνατή η ανάγνωση: ${e.message ?: "άγνωστο σφάλμα"}"
        }
    }

    private fun sortEntries(entries: List<FileEntry>): List<FileEntry> {
        val comparator = compareBy<FileEntry>({ !it.isDirectory }, { it.name.lowercase(Locale.getDefault()) })
        return if (sortAscending) entries.sortedWith(comparator) else entries.sortedWith(comparator.reversed())
    }

    private fun openEntry(entry: FileEntry) {
        if (entry.isDirectory) {
            currentDirectory?.let { navigationStack.addLast(it) }
            currentDirectory = entry.uri
            loadDirectory(requestFocus = true)
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(entry.uri, entry.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(entry.uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            } catch (_: ActivityNotFoundException) {
                toast("Δεν υπάρχει εγκατεστημένη εφαρμογή για αυτόν τον τύπο αρχείου.")
            }
        }
    }

    private fun navigateUp() {
        if (navigationStack.isEmpty()) {
            toast("Βρίσκεσαι ήδη στον αρχικό φάκελο.")
            return
        }
        currentDirectory = navigationStack.removeLast()
        loadDirectory(requestFocus = true)
    }

    private fun newFolder() {
        val repo = repository ?: return requireStorage()
        val dir = currentDirectory ?: return requireStorage()
        showTextInput("Νέος φάκελος", "Όνομα φακέλου", "Νέος φάκελος") { rawName ->
            val name = sanitizeName(rawName)
            if (name.isBlank()) return@showTextInput toast("Δώσε έγκυρο όνομα.")
            runFileTask("Δημιουργία φακέλου") {
                val created = repo.createFolder(dir, name)
                if (created == null) error("Δεν ήταν δυνατή η δημιουργία φακέλου.")
            }
        }
    }

    private fun copySelected(move: Boolean) {
        val entry = selectedEntry ?: return toast("Επίλεξε πρώτα αρχείο ή φάκελο.")
        clipboardUri = entry.uri
        clipboardMove = move
        statusText.text = if (move) {
            "Έτοιμο για μετακίνηση: ${entry.name}. Πήγαινε στον προορισμό και πάτησε Επικόλληση."
        } else {
            "Έτοιμο για αντιγραφή: ${entry.name}. Πήγαινε στον προορισμό και πάτησε Επικόλληση."
        }
    }

    private fun pasteClipboard() {
        val repo = repository ?: return requireStorage()
        val source = clipboardUri ?: return toast("Δεν υπάρχει κάτι για επικόλληση.")
        val destination = currentDirectory ?: return requireStorage()

        if (repo.isDestinationInsideSource(source, destination)) {
            return toast("Δεν μπορείς να αντιγράψεις φάκελο μέσα στον ίδιο ή σε υποφάκελό του.")
        }

        val moving = clipboardMove
        runFileTask(if (moving) "Μετακίνηση" else "Αντιγραφή") {
            if (moving) repo.moveRecursive(source, destination) else repo.copyRecursive(source, destination)
            if (moving) {
                clipboardUri = null
                clipboardMove = false
            }
        }
    }

    private fun renameSelected() {
        val repo = repository ?: return requireStorage()
        val entry = selectedEntry ?: return toast("Επίλεξε πρώτα αρχείο ή φάκελο.")
        showTextInput("Μετονομασία", "Νέο όνομα", entry.name) { rawName ->
            val newName = sanitizeName(rawName)
            if (newName.isBlank()) return@showTextInput toast("Δώσε έγκυρο όνομα.")
            runFileTask("Μετονομασία") {
                val renamed = repo.rename(entry.uri, newName)
                if (renamed == null) error("Η μετονομασία δεν υποστηρίζεται από αυτόν τον χώρο αποθήκευσης.")
            }
        }
    }

    private fun deleteSelected() {
        val repo = repository ?: return requireStorage()
        val entry = selectedEntry ?: return toast("Επίλεξε πρώτα αρχείο ή φάκελο.")

        AlertDialog.Builder(this)
            .setTitle("Διαγραφή")
            .setMessage("Να διαγραφεί οριστικά το «${entry.name}»;")
            .setNegativeButton("Άκυρο", null)
            .setPositiveButton("Διαγραφή") { _, _ ->
                runFileTask("Διαγραφή") {
                    if (!repo.delete(entry.uri)) error("Η διαγραφή απέτυχε.")
                }
            }
            .show()
    }

    private fun searchCurrentFolder() {
        if (repository == null) return requireStorage()
        showTextInput("Αναζήτηση", "Όνομα αρχείου ή φακέλου", "") { query ->
            val q = query.trim().lowercase(Locale.getDefault())
            val filtered = if (q.isBlank()) allEntries else allEntries.filter {
                it.name.lowercase(Locale.getDefault()).contains(q)
            }
            val sorted = sortEntries(filtered)
            adapter.submitList(sorted)
            selectedEntry = sorted.firstOrNull()
            statusText.text = if (q.isBlank()) {
                "${sorted.size} στοιχεία"
            } else {
                "${sorted.size} αποτελέσματα για «$query»"
            }
            if (sorted.isNotEmpty()) {
                fileGrid.post {
                    fileGrid.setSelection(0)
                    fileGrid.requestFocus()
                }
            }
        }
    }

    private fun toggleSort() {
        sortAscending = !sortAscending
        val sorted = sortEntries(allEntries)
        adapter.submitList(sorted)
        selectedEntry = sorted.firstOrNull()
        statusText.text = if (sortAscending) "Ταξινόμηση: Α → Ω" else "Ταξινόμηση: Ω → Α"
        if (sorted.isNotEmpty()) fileGrid.setSelection(0)
    }

    private fun showInfo() {
        val entry = selectedEntry ?: return toast("Επίλεξε πρώτα αρχείο ή φάκελο.")
        val modified = if (entry.lastModified > 0) {
            DateFormat.getDateTimeInstance().format(Date(entry.lastModified))
        } else {
            "Άγνωστο"
        }
        val body = buildString {
            appendLine("Όνομα: ${entry.name}")
            appendLine("Τύπος: ${if (entry.isDirectory) "Φάκελος" else entry.mimeType}")
            appendLine("Μέγεθος: ${if (entry.isDirectory) "—" else humanSize(entry.size)}")
            appendLine("Τροποποίηση: $modified")
            append("URI: ${entry.uri}")
        }
        AlertDialog.Builder(this)
            .setTitle("Πληροφορίες")
            .setMessage(body)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showTextInput(title: String, hint: String, initial: String, onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(initial)
            setSelection(text.length)
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("Άκυρο", null)
            .setPositiveButton("OK") { _, _ -> onOk(input.text.toString()) }
            .create()
        dialog.setOnShowListener { input.requestFocus() }
        dialog.show()
    }

    private fun runFileTask(label: String, task: () -> Unit) {
        statusText.text = "$label…"
        setControlsEnabled(false)

        Thread {
            val result = runCatching { task() }
            runOnUiThread {
                setControlsEnabled(true)
                result.onSuccess {
                    toast("$label ολοκληρώθηκε.")
                    loadDirectory(requestFocus = true)
                }.onFailure {
                    statusText.text = "$label απέτυχε: ${it.message ?: "άγνωστο σφάλμα"}"
                    toast("${it.message ?: "$label απέτυχε."}")
                }
            }
        }.start()
    }

    private fun setControlsEnabled(enabled: Boolean) {
        val ids = intArrayOf(
            R.id.btnStorage, R.id.btnUp, R.id.btnNewFolder, R.id.btnCopy,
            R.id.btnMove, R.id.btnPaste, R.id.btnRename, R.id.btnDelete,
            R.id.btnSearch, R.id.btnSort, R.id.btnInfo
        )
        ids.forEach { findViewById<View>(it).isEnabled = enabled }
        fileGrid.isEnabled = enabled
    }

    private fun requireStorage() {
        toast("Πάτησε πρώτα «Αποθήκευση» και επίλεξε φάκελο ή USB.")
    }

    private fun sanitizeName(raw: String): String =
        raw.trim().replace("/", "_").replace("\\", "_")

    private fun friendlyDocumentId(uri: Uri): String = try {
        DocumentsContract.getDocumentId(uri).substringAfterLast(':').ifBlank { "Αποθήκευση" }
    } catch (_: Exception) {
        "Αποθήκευση"
    }

    private fun humanSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
        return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    @Deprecated("Deprecated in Android, retained for API 26+ compatibility without extra dependencies")
    override fun onBackPressed() {
        if (navigationStack.isNotEmpty()) {
            navigateUp()
        } else {
            super.onBackPressed()
        }
    }
}
