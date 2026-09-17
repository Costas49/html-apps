package gr.costas.tvfilemanager

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException

class SafRepository(
    context: Context,
    val treeUri: Uri
) {
    private val resolver: ContentResolver = context.contentResolver

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_FLAGS
    )

    fun rootDocumentUri(): Uri {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
    }

    fun listChildren(directoryUri: Uri): List<FileEntry> {
        val parentId = DocumentsContract.getDocumentId(directoryUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val out = mutableListOf<FileEntry>()

        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val flagsIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)

            while (cursor.moveToNext()) {
                val documentId = cursor.getString(idIndex)
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                out += FileEntry(
                    uri = uri,
                    name = cursor.getString(nameIndex) ?: "Χωρίς όνομα",
                    mimeType = cursor.getString(mimeIndex) ?: "application/octet-stream",
                    size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L,
                    lastModified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) else 0L,
                    flags = if (flagsIndex >= 0 && !cursor.isNull(flagsIndex)) cursor.getInt(flagsIndex) else 0
                )
            }
        }
        return out
    }

    fun getEntry(documentUri: Uri): FileEntry? {
        resolver.query(documentUri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val flagsIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
            return FileEntry(
                uri = documentUri,
                name = cursor.getString(nameIndex) ?: "Χωρίς όνομα",
                mimeType = cursor.getString(mimeIndex) ?: "application/octet-stream",
                size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L,
                lastModified = if (modifiedIndex >= 0 && !cursor.isNull(modifiedIndex)) cursor.getLong(modifiedIndex) else 0L,
                flags = if (flagsIndex >= 0 && !cursor.isNull(flagsIndex)) cursor.getInt(flagsIndex) else 0
            )
        }
        return null
    }

    fun createFolder(parentUri: Uri, name: String): Uri? =
        DocumentsContract.createDocument(
            resolver,
            parentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name
        )

    fun rename(documentUri: Uri, newName: String): Uri? =
        DocumentsContract.renameDocument(resolver, documentUri, newName)

    fun delete(documentUri: Uri): Boolean =
        DocumentsContract.deleteDocument(resolver, documentUri)

    fun isDestinationInsideSource(sourceUri: Uri, destinationDirUri: Uri): Boolean {
        if (sourceUri.authority != destinationDirUri.authority) return false
        return try {
            val sourceId = DocumentsContract.getDocumentId(sourceUri).trimEnd('/')
            val destinationId = DocumentsContract.getDocumentId(destinationDirUri).trimEnd('/')
            destinationId == sourceId || destinationId.startsWith("$sourceId/")
        } catch (_: Exception) {
            false
        }
    }

    @Throws(IOException::class)
    fun copyRecursive(sourceUri: Uri, destinationDirectoryUri: Uri): Uri {
        val source = getEntry(sourceUri) ?: throw IOException("Το αρχείο δεν βρέθηκε.")

        if (source.isDirectory) {
            val children = listChildren(sourceUri)
            val newDirectory = createDocumentSafely(
                destinationDirectoryUri,
                DocumentsContract.Document.MIME_TYPE_DIR,
                source.name
            )
            for (child in children) {
                copyRecursive(child.uri, newDirectory)
            }
            return newDirectory
        }

        val mime = source.mimeType.ifBlank { "application/octet-stream" }
        val newFile = createDocumentSafely(destinationDirectoryUri, mime, source.name)

        resolver.openInputStream(sourceUri).use { input ->
            if (input == null) throw IOException("Δεν ήταν δυνατό το άνοιγμα του αρχείου.")
            resolver.openOutputStream(newFile, "w").use { output ->
                if (output == null) throw IOException("Δεν ήταν δυνατή η εγγραφή του αρχείου.")
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
                output.flush()
            }
        }
        return newFile
    }

    @Throws(IOException::class)
    fun moveRecursive(sourceUri: Uri, destinationDirectoryUri: Uri): Uri {
        val copied = copyRecursive(sourceUri, destinationDirectoryUri)
        if (!delete(sourceUri)) {
            throw IOException("Η αντιγραφή έγινε, αλλά το αρχικό αρχείο δεν διαγράφηκε.")
        }
        return copied
    }

    private fun createDocumentSafely(parentUri: Uri, mime: String, requestedName: String): Uri {
        DocumentsContract.createDocument(resolver, parentUri, mime, requestedName)?.let { return it }

        val fallbackName = addTimestamp(requestedName)
        return DocumentsContract.createDocument(resolver, parentUri, mime, fallbackName)
            ?: throw IOException("Δεν ήταν δυνατή η δημιουργία του προορισμού.")
    }

    private fun addTimestamp(name: String): String {
        val dot = name.lastIndexOf('.')
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        return if (dot > 0 && dot < name.length - 1) {
            name.substring(0, dot) + "_" + suffix + name.substring(dot)
        } else {
            name + "_" + suffix
        }
    }
}
