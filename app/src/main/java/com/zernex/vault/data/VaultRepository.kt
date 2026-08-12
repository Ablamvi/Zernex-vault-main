package com.zernex.vault.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.zernex.vault.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class VaultRepository(private val context: Context) {

    private val crypto = CryptoManager(context)
    private val indexFile = context.filesDir.resolve("vault_index.json")

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    suspend fun loadIndex() = withContext(Dispatchers.IO) {
        _items.value = readIndex()
    }

    suspend fun importUri(uri: Uri, deleteOriginalHint: Boolean = false): VaultItem? =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val name = queryDisplayName(uri) ?: "fichier_${System.currentTimeMillis()}"
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val size = querySize(uri)
            val id = UUID.randomUUID().toString()

            resolver.openInputStream(uri)?.use { input ->
                crypto.encryptToVault(id, input)
            } ?: return@withContext null

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
            item
        }

    suspend fun deleteItem(id: String) = withContext(Dispatchers.IO) {
        crypto.deleteVaultFile(id)
        val updated = _items.value.filterNot { it.id == id }
        writeIndex(updated)
        _items.value = updated
    }

    fun openDecryptedStream(id: String) = crypto.openDecrypted(id)

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
                    category = VaultCategory.valueOf(o.optString("cat", "OTHER")),
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
