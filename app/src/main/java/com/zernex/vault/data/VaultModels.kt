package com.zernex.vault.data

enum class LockType { PIN, PATTERN }

enum class VaultCategory {
    IMAGE, VIDEO, AUDIO, DOCUMENT, OTHER
}

enum class SortMode {
    DATE_DESC, DATE_ASC, NAME, SIZE, FAVORITES
}

data class VaultItem(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val category: VaultCategory,
    val sizeBytes: Long,
    val addedAt: Long,
    val favorite: Boolean = false
) {
    val sizeFormatted: String
        get() = when {
            sizeBytes < 1024 -> "$sizeBytes o"
            sizeBytes < 1024 * 1024 -> "%.1f Ko".format(sizeBytes / 1024.0)
            sizeBytes < 1024L * 1024 * 1024 -> "%.1f Mo".format(sizeBytes / (1024.0 * 1024))
            else -> "%.2f Go".format(sizeBytes / (1024.0 * 1024 * 1024))
        }

    val isVisual: Boolean
        get() = category == VaultCategory.IMAGE || category == VaultCategory.VIDEO
}

data class ImportProgress(
    val current: Int = 0,
    val total: Int = 0,
    val currentName: String = ""
) {
    val fraction: Float
        get() = if (total <= 0) 0f else current.toFloat() / total
    val label: String
        get() = if (total <= 0) "" else "$current / $total — $currentName"
}
