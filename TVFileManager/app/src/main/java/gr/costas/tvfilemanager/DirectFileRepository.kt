package gr.costas.tvfilemanager

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException

class DirectFileRepository(private val context: Context) {

    fun availableRoots(): List<File> {
        val roots = linkedMapOf<String, File>()

        addRoot(roots, Environment.getExternalStorageDirectory())

        val storage = File("/storage")
        storage.listFiles()?.forEach { candidate ->
            val name = candidate.name.lowercase()
            if (name == "emulated" || name == "self") return@forEach
            if (candidate.isDirectory && candidate.canRead()) addRoot(roots, candidate)
        }

        context.getExternalFilesDirs(null).forEach { appDir ->
            val path = appDir?.absolutePath ?: return@forEach
            val marker = "/Android/data/"
            val index = path.indexOf(marker)
            if (index > 0) addRoot(roots, File(path.substring(0, index)))
        }

        return roots.values.filter { it.exists() && it.isDirectory && it.canRead() }
    }

    private fun addRoot(map: MutableMap<String, File>, file: File?) {
        if (file == null) return
        try {
            map[file.canonicalPath] = file.canonicalFile
        } catch (_: Exception) {
            map[file.absolutePath] = file
        }
    }

    fun listChildren(directoryUri: Uri): List<FileEntry> {
        val directory = fileFromUri(directoryUri)
        return directory.listFiles()?.mapNotNull { file ->
            try { toEntry(file) } catch (_: Exception) { null }
        } ?: emptyList()
    }

    fun getEntry(uri: Uri): FileEntry? {
        val file = fileFromUri(uri)
        return if (file.exists()) toEntry(file) else null
    }

    fun createFolder(parentUri: Uri, name: String): Uri? {
        val parent = fileFromUri(parentUri)
        val target = uniqueTarget(parent, name)
        return if (target.mkdirs()) Uri.fromFile(target) else null
    }

    fun rename(uri: Uri, newName: String): Uri? {
        val source = fileFromUri(uri)
        val parent = source.parentFile ?: return null
        val target = uniqueTarget(parent, newName, ignore = source)
        return if (source.renameTo(target)) Uri.fromFile(target) else null
    }

    fun delete(uri: Uri): Boolean = deleteRecursive(fileFromUri(uri))

    fun isDestinationInsideSource(sourceUri: Uri, destinationDirUri: Uri): Boolean {
        return try {
            val source = fileFromUri(sourceUri).canonicalFile
            val destination = fileFromUri(destinationDirUri).canonicalFile
            destination == source || destination.path.startsWith(source.path + File.separator)
        } catch (_: Exception) {
            false
        }
    }

    @Throws(IOException::class)
    fun copyRecursive(sourceUri: Uri, destinationDirectoryUri: Uri): Uri {
        val source = fileFromUri(sourceUri)
        val destinationDirectory = fileFromUri(destinationDirectoryUri)
        if (!source.exists()) throw IOException("Το αρχείο δεν βρέθηκε.")
        if (!destinationDirectory.isDirectory) throw IOException("Ο προορισμός δεν είναι φάκελος.")

        val target = uniqueTarget(destinationDirectory, source.name)
        copyFileTree(source, target)
        return Uri.fromFile(target)
    }

    @Throws(IOException::class)
    fun moveRecursive(sourceUri: Uri, destinationDirectoryUri: Uri): Uri {
        val source = fileFromUri(sourceUri)
        val destinationDirectory = fileFromUri(destinationDirectoryUri)
        val target = uniqueTarget(destinationDirectory, source.name)

        if (source.renameTo(target)) return Uri.fromFile(target)

        copyFileTree(source, target)
        if (!deleteRecursive(source)) {
            throw IOException("Η αντιγραφή έγινε, αλλά το αρχικό αρχείο δεν διαγράφηκε.")
        }
        return Uri.fromFile(target)
    }

    fun fileFromUri(uri: Uri): File {
        val path = uri.path ?: throw IllegalArgumentException("Μη έγκυρη διαδρομή αρχείου.")
        return File(path)
    }

    fun toEntry(file: File): FileEntry {
        val mime = if (file.isDirectory) {
            DocumentsContract.Document.MIME_TYPE_DIR
        } else {
            mimeTypeFor(file)
        }
        return FileEntry(
            uri = Uri.fromFile(file),
            name = file.name.ifBlank { file.absolutePath },
            mimeType = mime,
            size = if (file.isFile) file.length() else 0L,
            lastModified = file.lastModified(),
            flags = 0
        )
    }

    private fun mimeTypeFor(file: File): String {
        val ext = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            "m3u", "m3u8" -> "application/x-mpegURL"
            "epub" -> "application/epub+zip"
            else -> "application/octet-stream"
        }
    }

    private fun copyFileTree(source: File, target: File) {
        if (source.isDirectory) {
            if (!target.exists() && !target.mkdirs()) throw IOException("Δεν δημιουργήθηκε ο φάκελος ${target.name}.")
            source.listFiles()?.forEach { child ->
                copyFileTree(child, File(target, child.name))
            }
        } else {
            target.parentFile?.mkdirs()
            source.inputStream().buffered().use { input ->
                target.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            target.setLastModified(source.lastModified())
        }
    }

    private fun deleteRecursive(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                if (!deleteRecursive(child)) return false
            }
        }
        return file.delete()
    }

    private fun uniqueTarget(parent: File, requestedName: String, ignore: File? = null): File {
        var candidate = File(parent, requestedName)
        if (!candidate.exists() || candidate == ignore) return candidate

        val base = requestedName.substringBeforeLast('.', requestedName)
        val ext = requestedName.substringAfterLast('.', "")
        var i = 2
        while (candidate.exists() && candidate != ignore) {
            val name = if (ext.isBlank() || base == requestedName) "$requestedName ($i)" else "$base ($i).$ext"
            candidate = File(parent, name)
            i++
        }
        return candidate
    }
}
