package com.zernex.vault.data

enum class LockType { PIN, PATTERN }

enum class VaultCategory {
    IMAGE, VIDEO, AUDIO, DOCUMENT, OTHER
}

data class VaultItem(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val category: VaultCategory,
    val sizeBytes: Long,
    val addedAt: Long,
    val originalPathHint: String = ""
) {
    val sizeFormatted: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1024) "%.1f Go".format(mb / 1024)
            else if (mb >= 1) "%.1f Mo".format(mb)
            else "${sizeBytes / 1024} Ko"
        }
}

fun mimeToCategory(mime: String): VaultCategory = when {
    mime.startsWith("image/") -> VaultCategory.IMAGE
    mime.startsWith("video/") -> VaultCategory.VIDEO
    mime.startsWith("audio/") -> VaultCategory.AUDIO
    mime.contains("pdf") || mime.contains("document") ||
        mime.contains("text") || mime.contains("msword") ||
        mime.contains("sheet") || mime.contains("presentation") -> VaultCategory.DOCUMENT
    else -> VaultCategory.OTHER
}
