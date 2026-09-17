package gr.costas.tvfilemanager

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.DateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    companion object {
        private const val REQ_STORAGE_PERMISSION = 7002
        private const val PREFS = "tv_file_manager"
        private const val PREF_ROOT_PATH = "root_path"
    }

    private lateinit var fileGrid: GridView
    private lateinit var pathText: TextView
    private lateinit var statusText: TextView
    private lateinit var adapter: FileGridAdapter
    private lateinit var repository: DirectFileRepository

    private var currentDirectory: File? = null
    private val navigationStack = ArrayDeque<File>()
    private var allEntries: List<FileEntry> = emptyList()
    private var selectedEntry: FileEntry? = null
    private var sortAscending = true
    private var clipboardUri: Uri? = null
    private var clipboardMove = false
    private var awaitingStoragePermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = DirectFileRepository(this)
        fileGrid = findViewById(R.id.fileGrid)
        pathText = findViewById(R.id.pathText)
        statusText = findViewById(R.id.statusText)
        adapter = FileGridAdapter(this)
        fileGrid.adapter = adapter

        wireGrid()
        wireButtons()
        restoreStorage()
    }

    override fun onResume() {
        super.onResume()
        if (awaitingStoragePermission && hasStorageAccess()) {
            awaitingStoragePermission = false
            showStorageRoots()
        }
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
            } else false
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
        if (!hasStorageAccess()) {
            requestStorageAccess()
            return
        }
        showStorageRoots()
    }

    private fun hasStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStorageAccess() {
        awaitingStoragePermission = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val packageUri = Uri.parse("package:$packageName")
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri))
            } catch (_: ActivityNotFoundException) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: ActivityNotFoundException) {
                    awaitingStoragePermission = false
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
                    toast("Άνοιξε τα δικαιώματα της εφαρμογής και επίτρεψε πρόσβαση στα αρχεία.")
                }
            }
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQ_STORAGE_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STORAGE_PERMISSION) {
            awaitingStoragePermission = false
            if (hasStorageAccess()) showStorageRoots()
            else toast("Χρειάζεται άδεια πρόσβασης στα αρχεία για να λειτουργήσει ο File Manager.")
        }
    }

    private fun showStorageRoots() {
        val roots = repository.availableRoots()
        if (roots.isEmpty()) {
            statusText.text = "Δεν βρέθηκε προσβάσιμος χώρος αποθήκευσης."
            return
        }

        if (roots.size == 1) {
            attachStorage(roots.first())
            return
        }

        val internalPath = try { Environment.getExternalStorageDirectory().canonicalPath } catch (_: Exception) { "" }
        val labels = roots.map { root ->
            val path = try { root.canonicalPath } catch (_: Exception) { root.absolutePath }
            if (path == internalPath) "Εσωτερικός χώρος\n$path" else "USB / Εξωτερικός χώρος\n$path"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Επίλεξε χώρο αποθήκευσης")
            .setItems(labels) { _, which -> attachStorage(roots[which]) }
            .setNegativeButton("Άκυρο", null)
            .show()
    }

    private fun restoreStorage() {
        if (!hasStorageAccess()) {
            adapter.submitList(emptyList())
            pathText.text = "Χρειάζεται πρόσβαση στα αρχεία"
            statusText.text = "Πάτησε «Χώρος / USB» και ενεργοποίησε μία φορά τη «Διαχείριση όλων των αρχείων»."
            findViewById<Button>(R.id.btnStorage).requestFocus()
            return
        }

        val saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_ROOT_PATH, null)
        val savedRoot = saved?.let { File(it) }
        if (savedRoot != null && savedRoot.exists() && savedRoot.isDirectory && savedRoot.canRead()) {
            attachStorage(savedRoot)
        } else {
            showStorageRoots()
        }
    }

    private fun attachStorage(root: File) {
        navigationStack.clear()
        currentDirectory = root
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_ROOT_PATH, root.absolutePath).apply()
        loadDirectory(requestFocus = true)
    }

    private fun loadDirectory(requestFocus: Boolean = false) {
        val dir = currentDirectory ?: return requireStorage()
        try {
            allEntries = repository.listChildren(Uri.fromFile(dir))
            val sorted = sortEntries(allEntries)
            adapter.submitList(sorted)
            selectedEntry = sorted.firstOrNull()
            pathText.text = dir.absolutePath
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
            val next = repository.fileFromUri(entry.uri)
            currentDirectory?.let { navigationStack.addLast(it) }
            currentDirectory = next
            loadDirectory(requestFocus = true)
            return
        }

        val file = repository.fileFromUri(entry.uri)
        val contentUri = try {
            FileProvider.getUriForFile(this, "$packageName.files", file)
        } catch (e: Exception) {
            return toast("Δεν ήταν δυνατό το άνοιγμα: ${e.message ?: "σφάλμα πρόσβασης"}")
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, entry.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            } catch (_: ActivityNotFoundException) {
                toast("Δεν υπάρχει εγκατεστημένη εφαρμογή για αυτόν τον τύπο αρχείου.")
            }
        }
    }

    private fun navigateUp() {
        if (navigationStack.isEmpty()) {
            toast("Βρίσκεσαι ήδη στον αρχικό χώρο.")
            return
        }
        currentDirectory = navigationStack.removeLast()
        loadDirectory(requestFocus = true)
    }

    override fun onBackPressed() {
        if (navigationStack.isNotEmpty()) navigateUp() else super.onBackPressed()
    }

    private fun newFolder() {
        val dir = currentDirectory ?: return requireStorage()
        showTextInput("Νέος φάκελος", "Όνομα φακέλου", "Νέος φάκελος") { rawName ->
            val name = sanitizeName(rawName)
            if (name.isBlank()) return@showTextInput toast("Δώσε έγκυρο όνομα.")
            runFileTask("Δημιουργία φακέλου") {
                if (repository.createFolder(Uri.fromFile(dir), name) == null) error("Δεν ήταν δυνατή η δημιουργία φακέλου.")
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
        val source = clipboardUri ?: return toast("Δεν υπάρχει κάτι για επικόλληση.")
        val destination = currentDirectory ?: return requireStorage()
        val destinationUri = Uri.fromFile(destination)

        if (repository.isDestinationInsideSource(source, destinationUri)) {
            return toast("Δεν μπορείς να αντιγράψεις φάκελο μέσα στον ίδιο ή σε υποφάκελό του.")
        }

        val moving = clipboardMove
        runFileTask(if (moving) "Μετακίνηση" else "Αντιγραφή") {
            if (moving) repository.moveRecursive(source, destinationUri)
            else repository.copyRecursive(source, destinationUri)
            if (moving) {
                clipboardUri = null
                clipboardMove = false
            }
        }
    }

    private fun renameSelected() {
        val entry = selectedEntry ?: return toast("Επίλεξε πρώτα αρχείο ή φάκελο.")
        showTextInput("Μετονομασία", "Νέο όνομα", entry.name) { rawName ->
            val newName = sanitizeName(rawName)
            if (newName.isBlank()) return@showTextInput toast("Δώσε έγκυρο όνομα.")
            runFileTask("Μετονομασία") {
                if (repository.rename(entry.uri, newName) == null) error("Η μετονομασία απέτυχε.")
            }
        }
    }

    private fun deleteSelected() {
        val entry = selectedEntry ?: return toast("Επίλεξε πρώτα αρχείο ή φάκελο.")
        AlertDialog.Builder(this)
            .setTitle("Διαγραφή")
            .setMessage("Να διαγραφεί οριστικά το «${entry.name}»;")
            .setNegativeButton("Άκυρο", null)
            .setPositiveButton("Διαγραφή") { _, _ ->
                runFileTask("Διαγραφή") {
                    if (!repository.delete(entry.uri)) error("Η διαγραφή απέτυχε.")
                }
            }
            .show()
    }

    private fun searchCurrentFolder() {
        if (currentDirectory == null) return requireStorage()
        showTextInput("Αναζήτηση", "Όνομα αρχείου ή φακέλου", "") { query ->
            val q = query.trim().lowercase(Locale.getDefault())
            val filtered = if (q.isBlank()) allEntries else allEntries.filter {
                it.name.lowercase(Locale.getDefault()).contains(q)
            }
            val sorted = sortEntries(filtered)
            adapter.submitList(sorted)
            selectedEntry = sorted.firstOrNull()
            statusText.text = if (q.isBlank()) "${sorted.size} στοιχεία" else "${sorted.size} αποτελέσματα για «$query»"
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
        val modified = if (entry.lastModified > 0) DateFormat.getDateTimeInstance().format(Date(entry.lastModified)) else "Άγνωστο"
        val file = repository.fileFromUri(entry.uri)
        val body = buildString {
            appendLine("Όνομα: ${entry.name}")
            appendLine("Τύπος: ${if (entry.isDirectory) "Φάκελος" else entry.mimeType}")
            appendLine("Μέγεθος: ${if (entry.isDirectory) "—" else humanSize(entry.size)}")
            appendLine("Τροποποίηση: $modified")
            append("Διαδρομή: ${file.absolutePath}")
        }
        AlertDialog.Builder(this)
            .setTitle("Πληροφορίες")
            .setMessage(body)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showTextInput(title: String, hint: String, initial: String, onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setHint(hint)
            setText(initial)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("Άκυρο", null)
            .setPositiveButton("OK") { _, _ -> onOk(input.text?.toString().orEmpty()) }
            .show()
    }

    private fun runFileTask(label: String, action: () -> Unit) {
        statusText.text = "$label…"
        Thread {
            try {
                action()
                runOnUiThread {
                    statusText.text = "$label ολοκληρώθηκε."
                    loadDirectory(requestFocus = true)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "$label απέτυχε: ${e.message ?: "άγνωστο σφάλμα"}"
                }
            }
        }.start()
    }

    private fun sanitizeName(value: String): String = value.trim().replace('/', '_').replace('\\', '_')

    private fun requireStorage() {
        toast("Πάτησε πρώτα «Χώρος / USB».")
        findViewById<Button>(R.id.btnStorage).requestFocus()
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
}
