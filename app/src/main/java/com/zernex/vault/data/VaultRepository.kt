package com.zernex.vault.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.zernex.vault.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class VaultRepository(private val context: Context) {

    private val crypto = CryptoManager(context)
    private val vaultDir = File(context.filesDir, "vault").also { if (!it.exists()) it.mkdirs() }
    private val indexFile = File(vaultDir, "index.json")
    private val cacheDir = File(context.cacheDir, "vault_open").also { if (!it.exists()) it.mkdirs() }
    private val thumbDir = File(context.filesDir, "vault_thumbs").also { if (!it.exists()) it.mkdirs() }

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    fun thumbnailFile(id: String): File = File(thumbDir, "$id.jpg")

    fun hasThumbnail(id: String): Boolean {
        val f = thumbnailFile(id)
        return f.exists() && f.length() > 0
    }

    suspend fun loadIndex() = withContext(Dispatchers.IO) {
        _items.value = readIndex()
    }

    fun totalBytes(): Long = _items.value.sumOf { it.sizeBytes }

    suspend fun importUri(uri: Uri, deleteOriginal: Boolean): Pair<VaultItem?, Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val name = queryDisplayName(uri) ?: "fichier_${System.currentTimeMillis()}"
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val size = querySize(uri)
                val id = UUID.randomUUID().toString()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    crypto.encryptToVault(id, input)
                } ?: return@withContext null to false

                val item = VaultItem(
                    id = id,
                    displayName = name,
                    mimeType = mime,
                    category = mimeToCategory(mime),
                    sizeBytes = size,
                    addedAt = System.currentTimeMillis()
                )
                val updated = _items.value + item
                writeIndex(updated)
                _items.value = updated

                // Miniature en arrière-plan immédiat pour image/vidéo
                if (item.isVisual) {
                    runCatching { generateThumbnail(item) }
                }

                var deleted = false
                if (deleteOriginal) {
                    deleted = tryDeleteOriginal(uri)
                }
                item to deleted
            } catch (_: Exception) {
                null to false
            }
        }

    /** Génère (si besoin) une vignette JPEG privée pour image ou vidéo. */
    suspend fun ensureThumbnail(item: VaultItem): File? = withContext(Dispatchers.IO) {
        val out = thumbnailFile(item.id)
        if (out.exists() && out.length() > 0) return@withContext out
        if (!item.isVisual) return@withContext null
        generateThumbnail(item)
    }

    private fun generateThumbnail(item: VaultItem): File? {
        val out = thumbnailFile(item.id)
        try {
            val temp = decryptToCacheBlocking(item.id, item.displayName) ?: return null
            val bitmap: Bitmap? = when {
                item.mimeType.startsWith("image/") -> decodeSampled(temp, 512)
                item.mimeType.startsWith("video/") -> {
                    val r = MediaMetadataRetriever()
                    try {
                        r.setDataSource(temp.absolutePath)
                        r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: r.frameAtTime
                    } finally {
                        r.release()
                    }
                }
                else -> null
            }
            if (bitmap == null) return null
            val scaled = scaleMax(bitmap, 512)
            FileOutputStream(out).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, fos)
            }
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            return out
        } catch (_: Exception) {
            out.delete()
            return null
        }
    }

    private fun decodeSampled(file: File, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        val w = bounds.outWidth
        val h = bounds.outHeight
        while (w / sample > maxSize || h / sample > maxSize) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun scaleMax(src: Bitmap, max: Int): Bitmap {
        val w = src.width
        val h = src.height
        val scale = maxOf(w, h).toFloat() / max
        if (scale <= 1f) return src
        return Bitmap.createScaledBitmap(src, (w / scale).toInt(), (h / scale).toInt(), true)
    }

    private fun tryDeleteOriginal(uri: Uri): Boolean {
        return try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                return DocumentsContract.deleteDocument(context.contentResolver, uri)
            }
            context.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteItem(id: String) = withContext(Dispatchers.IO) {
        crypto.deleteVaultFile(id)
        thumbnailFile(id).delete()
        val updated = _items.value.filterNot { it.id == id }
        writeIndex(updated)
        _items.value = updated
        cacheDir.listFiles()?.filter { it.name.startsWith(id) }?.forEach { it.delete() }
    }

    suspend fun decryptToCache(id: String, displayName: String): File? = withContext(Dispatchers.IO) {
        decryptToCacheBlocking(id, displayName)
    }

    private fun decryptToCacheBlocking(id: String, displayName: String): File? {
        return try {
            val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val out = File(cacheDir, "${id}_$safeName")
            if (out.exists() && out.length() > 0) return out
            if (out.exists()) out.delete()
            crypto.openDecrypted(id).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            out
        } catch (_: Exception) {
            null
        }
    }

    suspend fun restoreToDevice(item: VaultItem, removeFromVault: Boolean): Uri? =
        withContext(Dispatchers.IO) {
            try {
                val temp = decryptToCacheBlocking(item.id, item.displayName) ?: return@withContext null
                val uri = writeToMediaStore(item, temp) ?: return@withContext null
                if (removeFromVault) {
                    crypto.deleteVaultFile(item.id)
                    thumbnailFile(item.id).delete()
                    val updated = _items.value.filterNot { it.id == item.id }
                    writeIndex(updated)
                    _items.value = updated
                    temp.delete()
                }
                uri
            } catch (_: Exception) {
                null
            }
        }

    private fun writeToMediaStore(item: VaultItem, file: File): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relative = when (item.category) {
                    VaultCategory.IMAGE -> Environment.DIRECTORY_PICTURES + "/ZERNEX"
                    VaultCategory.VIDEO -> Environment.DIRECTORY_MOVIES + "/ZERNEX"
                    VaultCategory.AUDIO -> Environment.DIRECTORY_MUSIC + "/ZERNEX"
                    else -> Environment.DIRECTORY_DOWNLOADS + "/ZERNEX"
                }
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val collection = when {
            item.mimeType.startsWith("image/") ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            item.mimeType.startsWith("video/") ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            item.mimeType.startsWith("audio/") ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Files.getContentUri("external")
        }
        val outUri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(outUri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(outUri, values, null, null)
        }
        return outUri
    }

    fun clearOpenCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /** Régénère les miniatures manquantes (après upgrade). */
    suspend fun backfillThumbnails() = withContext(Dispatchers.IO) {
        _items.value.filter { it.isVisual && !hasThumbnail(it.id) }.forEach { item ->
            runCatching { generateThumbnail(item) }
        }
    }

    private fun mimeToCategory(mime: String): VaultCategory = when {
        mime.startsWith("image/") -> VaultCategory.IMAGE
        mime.startsWith("video/") -> VaultCategory.VIDEO
        mime.startsWith("audio/") -> VaultCategory.AUDIO
        mime.contains("pdf") || mime.contains("document") || mime.contains("text") ||
            mime.contains("sheet") || mime.contains("presentation") || mime.contains("msword") ||
            mime.contains("officedocument") -> VaultCategory.DOCUMENT
        else -> VaultCategory.OTHER
    }

    private fun readIndex(): List<VaultItem> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                VaultItem(
                    id = o.getString("id"),
                    displayName = o.getString("name"),
                    mimeType = o.getString("mime"),
                    category = runCatching {
                        VaultCategory.valueOf(o.optString("cat", "OTHER"))
                    }.getOrDefault(VaultCategory.OTHER),
                    sizeBytes = o.optLong("size", 0L),
                    addedAt = o.optLong("added", 0L)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeIndex(items: List<VaultItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("name", item.displayName)
                put("mime", item.mimeType)
                put("cat", item.category.name)
                put("size", item.sizeBytes)
                put("added", item.addedAt)
            })
        }
        indexFile.writeText(arr.toString())
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }
        return null
    }

    private fun querySize(uri: Uri): Long {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && c.moveToFirst()) return c.getLong(idx)
        }
        return 0L
    }
}
