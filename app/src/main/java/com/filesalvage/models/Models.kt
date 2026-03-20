package com.filesalvage.models

import android.net.Uri

enum class FileType(val label: String, val extensions: List<String>) {
    PHOTO("Photo", listOf("jpg", "jpeg", "png", "heic", "webp", "gif", "bmp", "raw", "cr2", "nef")),
    VIDEO("Video", listOf("mp4", "mov", "avi", "mkv", "3gp", "wmv", "flv", "m4v")),
    AUDIO("Audio", listOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma")),
    DOCUMENT("Document", listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip", "rar")),
    APK("APK", listOf("apk")),
    OTHER("Other", emptyList());

    companion object {
        fun fromExtension(ext: String): FileType {
            val lower = ext.lowercase()
            return values().firstOrNull { it.extensions.contains(lower) } ?: OTHER
        }
    }
}

data class RecoverableFile(
    val id: String,
    val name: String,
    val fileType: FileType,
    val size: Long,
    val originalPath: String,
    val deletedDate: Long?,
    val recoveryChance: Float,
    val fragmentCount: Int,
    val uri: Uri? = null,
    val thumbnailUri: Uri? = null
) {
    val formattedSize: String get() {
        return when {
            size >= 1_000_000_000 -> String.format("%.1f GB", size / 1_000_000_000.0)
            size >= 1_000_000     -> String.format("%.1f MB", size / 1_000_000.0)
            size >= 1_000         -> String.format("%.1f KB", size / 1_000.0)
            else                  -> "$size B"
        }
    }

    val formattedDeletedDate: String get() {
        if (deletedDate == null) return "Unknown"
        val diff = System.currentTimeMillis() - deletedDate
        val days = diff / 86_400_000
        return when {
            days == 0L  -> "Today"
            days == 1L  -> "Yesterday"
            days < 7L   -> "$days days ago"
            days < 30L  -> "${days / 7} weeks ago"
            days < 365L -> "${days / 30} months ago"
            else        -> "${days / 365} years ago"
        }
    }
}

data class ScanResult(
    val files: List<RecoverableFile>,
    val totalFound: Int,
    val scanDurationMs: Long,
    val scanType: ScanType
)

enum class ScanType { QUICK, DEEP }

enum class ScanState { IDLE, SCANNING, COMPLETED, ERROR }

data class ScanProgress(
    val step: String,
    val stepIndex: Int,
    val totalSteps: Int,
    val filesFound: Int
) {
    val percent: Float get() = if (totalSteps == 0) 0f else stepIndex.toFloat() / totalSteps
}

enum class RecoveryDestination { GALLERY, DOWNLOADS, CUSTOM }
