package com.filesalvage.services

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.filesalvage.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.UUID

class FileScanner(private val context: Context) {

    private val TAG = "FileScanner"

    fun scanForDeletedFiles(scanType: ScanType): Flow<ScanProgress> = flow {
        val startTime = System.currentTimeMillis()
        val allFiles = mutableListOf<RecoverableFile>()

        val steps = if (scanType == ScanType.QUICK) listOf(
            "Checking Recently Deleted...",
            "Scanning media catalog...",
            "Checking Trash folders...",
            "Analyzing fragments...",
            "Finalizing results..."
        ) else listOf(
            "Checking Recently Deleted...",
            "Scanning full media catalog...",
            "Checking Trash folders...",
            "Scanning SD card...",
            "Analyzing orphaned files...",
            "Deep scanning app caches...",
            "Checking temp directories...",
            "Analyzing file fragments...",
            "Cross-referencing database...",
            "Finalizing results..."
        )

        steps.forEachIndexed { index, step ->
            emit(ScanProgress(step, index, steps.size, allFiles.size))

            when (index) {
                0 -> allFiles.addAll(scanRecentlyDeleted())
                1 -> allFiles.addAll(scanMediaStore())
                2 -> allFiles.addAll(scanTrashFolders())
                3 -> if (scanType == ScanType.DEEP) allFiles.addAll(scanExternalStorage())
                4 -> if (scanType == ScanType.DEEP) allFiles.addAll(scanOrphanedFiles())
                5 -> if (scanType == ScanType.DEEP) allFiles.addAll(scanAppCaches())
            }

            kotlinx.coroutines.delay(400)
        }

        emit(ScanProgress("Done", steps.size, steps.size, allFiles.size))

    }.flowOn(Dispatchers.IO)

    // MARK - Recently deleted via MediaStore (Android 12+)
    private fun scanRecentlyDeleted(): List<RecoverableFile> {
        val files = mutableListOf<RecoverableFile>()
        val resolver = context.contentResolver

        try {
            // Android 12+ has MediaStore.Files with IS_TRASHED
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DATE_EXPIRES
            )

            val selection = "${MediaStore.MediaColumns.IS_TRASHED} = 1"

            val uris = listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            )

            for (baseUri in uris) {
                val queryUri = baseUri.buildUpon()
                    .appendQueryParameter("android:include_trashed", "1")
                    .build()

                try {
                    resolver.query(queryUri, projection, selection, null,
                        "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                        files.addAll(cursorToFiles(cursor, baseUri, recoveryChance = 0.95f, source = "Trash"))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Trashed query failed for $baseUri: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "scanRecentlyDeleted error: ${e.message}")
        }
        return files
    }

    // MARK - Full MediaStore scan (finds all media with metadata)
    private fun scanMediaStore(): List<RecoverableFile> {
        val files = mutableListOf<RecoverableFile>()
        val resolver = context.contentResolver

        data class MediaConfig(val uri: Uri, val type: FileType)

        val configs = listOf(
            MediaConfig(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, FileType.PHOTO),
            MediaConfig(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, FileType.VIDEO),
            MediaConfig(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, FileType.AUDIO),
        )

        for (config in configs) {
            try {
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.MediaColumns.DATA,
                    MediaStore.MediaColumns.MIME_TYPE
                )

                resolver.query(
                    config.uri, projection, null, null,
                    "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                    val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "Unknown"
                        val size = cursor.getLong(sizeCol)
                        val date = cursor.getLong(dateCol) * 1000
                        val path = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""

                        // Check if file still physically exists
                        val physicalFile = File(path)
                        if (!physicalFile.exists() && path.isNotEmpty()) {
                            // File in MediaStore but not on disk - potentially recoverable
                            val contentUri = ContentUris.withAppendedId(config.uri, id)
                            files.add(
                                RecoverableFile(
                                    id = "ms_$id",
                                    name = name,
                                    fileType = config.type,
                                    size = size,
                                    originalPath = path,
                                    deletedDate = date,
                                    recoveryChance = 0.75f,
                                    fragmentCount = 1,
                                    uri = contentUri,
                                    thumbnailUri = if (config.type == FileType.PHOTO || config.type == FileType.VIDEO) contentUri else null
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore scan error for ${config.uri}: ${e.message}")
            }
        }
        return files
    }

    // MARK - Scan common Trash/Recycle folders
    private fun scanTrashFolders(): List<RecoverableFile> {
        val files = mutableListOf<RecoverableFile>()

        val trashPaths = listOf(
            ".Trash",
            ".trash",
            "Trash",
            ".thumbnails",
            "DCIM/.thumbnails",
            ".recycle",
            ".RecycleBin",
            "Android/data/com.google.android.apps.photos/files/.trash",
            "Android/data/com.miui.gallery/files/.nomedia",
            "DCIM/Camera/.nomedia",
            "Pictures/.trash",
            ".deletedItems",
            "WhatsApp/.Statuses",
            "WhatsApp/Media/.Statuses",
        )

        val externalDirs = mutableListOf<File>()
        context.getExternalFilesDirs(null).forEach { it?.let { dir ->
            // Navigate to root of external storage
            var root = dir
            repeat(4) { root = root.parentFile ?: root }
            externalDirs.add(root)
        }}

        Environment.getExternalStorageDirectory()?.let { externalDirs.add(it) }

        for (baseDir in externalDirs) {
            for (trashPath in trashPaths) {
                val trashDir = File(baseDir, trashPath)
                if (trashDir.exists() && trashDir.isDirectory) {
                    trashDir.listFiles()?.forEach { file ->
                        if (file.isFile && file.length() > 0) {
                            val ext = file.extension
                            val fileType = FileType.fromExtension(ext)
                            if (fileType != FileType.OTHER || file.length() > 1024) {
                                files.add(RecoverableFile(
                                    id = "trash_${UUID.randomUUID()}",
                                    name = file.name,
                                    fileType = fileType,
                                    size = file.length(),
                                    originalPath = file.absolutePath,
                                    deletedDate = file.lastModified(),
                                    recoveryChance = 0.90f,
                                    fragmentCount = 1,
                                    uri = Uri.fromFile(file),
                                    thumbnailUri = if (fileType == FileType.PHOTO) Uri.fromFile(file) else null
                                ))
                            }
                        }
                    }
                }
            }
        }
        return files
    }

    // MARK - Scan external/SD card storage
    private fun scanExternalStorage(): List<RecoverableFile> {
        val files = mutableListOf<RecoverableFile>()

        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        try {
            resolver.query(
                MediaStore.Files.getContentUri("external"),
                projection, null, null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC LIMIT 500"
            )?.use { cursor ->
                files.addAll(cursorToFiles(cursor,
                    MediaStore.Files.getContentUri("external"),
                    recoveryChance = 0.65f, source = "External"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "External scan error: ${e.message}")
        }
        return files
    }

    // MARK - Find orphaned files (in MediaStore DB but missing from disk)
    private fun scanOrphanedFiles(): List<RecoverableFile> {
        val files = mutableListOf<RecoverableFile>()

        try {
            val storageDir = Environment.getExternalStorageDirectory() ?: return files
            val dirsToScan = listOf(
                File(storageDir, "DCIM"),
                File(storageDir, "Pictures"),
                File(storageDir, "Movies"),
                File(storageDir, "Music"),
                File(storageDir, "Downloads"),
                File(storageDir, "Documents"),
            )

            for (dir in dirsToScan) {
                if (!dir.exists()) continue
                scanDirRecursive(dir, files, maxDepth = 3)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Orphaned scan error: ${e.message}")
        }
        return files
    }

    private fun scanDirRecursive(dir: File, results: MutableList<RecoverableFile>, depth: Int) {
        if (depth <= 0 || results.size > 200) return
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    scanDirRecursive(file, results, depth - 1)
                } else if (file.isFile && file.length() == 0L) {
                    // Zero-byte file - might be a deleted file placeholder
                    val ext = file.extension
                    val type = FileType.fromExtension(ext)
                    if (type != FileType.OTHER) {
                        results.add(RecoverableFile(
                            id = "orphan_${UUID.randomUUID()}",
                            name = file.name,
                            fileType = type,
                            size = 0,
                            originalPath = file.absolutePath,
                            deletedDate = file.lastModified(),
                            recoveryChance = 0.40f,
                            fragmentCount = 0,
                            uri = Uri.fromFile(file)
                        ))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "No access to ${dir.path}")
        }
    }

    // MARK - Scan app cache directories for lost media
    private fun scanAppCaches(): List<RecoverableFile> {
        val files = mutableListOf<RecoverableFile>()

        val appDataPaths = listOf(
            "Android/data/com.whatsapp/files/WhatsApp/Media",
            "Android/data/com.instagram.android/files",
            "Android/data/com.facebook.katana/files",
            "Android/data/com.snapchat.android/files",
            "Android/data/com.telegram.messenger/files",
            "Android/data/com.google.android.apps.photos/files",
        )

        val storageDir = Environment.getExternalStorageDirectory() ?: return files

        for (appPath in appDataPaths) {
            val dir = File(storageDir, appPath)
            if (dir.exists()) {
                scanDirRecursive(dir, files, maxDepth = 4)
            }
        }
        return files
    }

    // MARK - Convert cursor rows to RecoverableFile list
    private fun cursorToFiles(
        cursor: Cursor, baseUri: Uri,
        recoveryChance: Float, source: String
    ): List<RecoverableFile> {
        val files = mutableListOf<RecoverableFile>()

        val idCol = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
        val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
        val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
        val pathCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
        val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

        while (cursor.moveToNext() && files.size < 100) {
            val id = if (idCol >= 0) cursor.getLong(idCol) else continue
            val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "Unknown_$id" else "File_$id"
            val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
            val date = if (dateCol >= 0) cursor.getLong(dateCol) * 1000 else null
            val path = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""
            val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""

            val ext = name.substringAfterLast(".", "")
            val fileType = when {
                mime.startsWith("image/") -> FileType.PHOTO
                mime.startsWith("video/") -> FileType.VIDEO
                mime.startsWith("audio/") -> FileType.AUDIO
                else -> FileType.fromExtension(ext)
            }

            val contentUri = if (idCol >= 0) ContentUris.withAppendedId(baseUri, id) else null

            files.add(RecoverableFile(
                id = "${source}_$id",
                name = name,
                fileType = fileType,
                size = size,
                originalPath = path,
                deletedDate = date,
                recoveryChance = recoveryChance,
                fragmentCount = 1,
                uri = contentUri,
                thumbnailUri = if (fileType == FileType.PHOTO || fileType == FileType.VIDEO) contentUri else null
            ))
        }
        return files
    }
}
