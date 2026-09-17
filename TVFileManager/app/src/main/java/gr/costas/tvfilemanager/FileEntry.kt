package gr.costas.tvfilemanager

import android.net.Uri
import android.provider.DocumentsContract

data class FileEntry(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val flags: Int
) {
    val isDirectory: Boolean
        get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}
