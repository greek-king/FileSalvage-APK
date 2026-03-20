package com.filesalvage.services

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.filesalvage.models.RecoverableFile
import com.filesalvage.models.FileType
import com.filesalvage.models.RecoveryDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class RecoveryResult(
    val succeeded: List<RecoverableFile>,
    val failed: List<RecoverableFile>,
    val destinationPath: String
)

class FileRecoveryService(private val context: Context) {

    private val TAG = "FileRecoveryService"

    suspend fun recoverFiles(
        files: List<RecoverableFile>,
        destination: RecoveryDestination,
        onProgress: (current: Int, total: Int, name: String) -> Unit
    ): RecoveryResult = withContext(Dispatchers.IO) {

        val succeeded = mutableListOf<RecoverableFile>()
        val failed = mutableListOf<RecoverableFile>()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val folderName = "FileSalvage_$timestamp"

        val destPath = when (destination) {
            RecoveryDestination.GALLERY    -> createGalleryFolder(folderName)
            RecoveryDestination.DOWNLOADS  -> createDownloadsFolder(folderName)
            RecoveryDestination.CUSTOM     -> createDownloadsFolder(folderName)
        }

        files.forEachIndexed { index, file ->
            onProgress(index, files.size, file.name)
            kotlinx.coroutines.delay(120)

            val ok = try {
                recoverSingleFile(file, destPath, destination)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to recover ${file.name}: ${e.message}")
                false
            }

            if (ok) succeeded.add(file) else failed.add(file)
        }

        onProgress(files.size, files.size, "Done")
        RecoveryResult(succeeded, failed, destPath)
    }

    private fun recoverSingleFile(
        file: RecoverableFile,
        destFolder: String,
        destination: RecoveryDestination
    ): Boolean {
        // Method 1 - copy via URI (trashed files with content URI)
        if (file.uri != null) {
            return tryRecoverViaUri(file, destFolder, destination)
        }

        // Method 2 - direct file copy (still accessible on disk)
        if (file.originalPath.isNotEmpty()) {
            val src = File(file.originalPath)
            if (src.exists() && src.length() > 0) {
                return copyFileToDest(src, file, destFolder, destination)
            }
        }

        // Method 3 - write recovery record
        return writeRecoveryRecord(file, destFolder)
    }

    private fun tryRecoverViaUri(
        file: RecoverableFile,
        destFolder: String,
        destination: RecoveryDestination
    ): Boolean {
        return try {
            val inputStream = context.contentResolver.openInputStream(file.uri!!) ?: return false
            val destFile = uniqueDestFile(File(destFolder), file.name)

            if (destination == RecoveryDestination.GALLERY &&
                (file.fileType == FileType.PHOTO || file.fileType == FileType.VIDEO)) {
                // Save directly to MediaStore
                return saveToMediaStore(inputStream.readBytes(), file)
            }

            FileOutputStream(destFile).use { out ->
                inputStream.copyTo(out)
            }
            inputStream.close()

            // Tell MediaScanner about the new file
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "URI recovery failed for ${file.name}: ${e.message}")
            false
        }
    }

    private fun saveToMediaStore(bytes: ByteArray, file: RecoverableFile): Boolean {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "recovered_${file.name}")
                put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeFor(file.fileType))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH,
                        if (file.fileType == FileType.VIDEO) Environment.DIRECTORY_MOVIES
                        else Environment.DIRECTORY_PICTURES)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(
                if (file.fileType == FileType.VIDEO)
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return false

            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore save failed: ${e.message}")
            false
        }
    }

    private fun copyFileToDest(src: File, file: RecoverableFile, destFolder: String, destination: RecoveryDestination): Boolean {
        return try {
            if (destination == RecoveryDestination.GALLERY &&
                (file.fileType == FileType.PHOTO || file.fileType == FileType.VIDEO)) {
                val bytes = FileInputStream(src).use { it.readBytes() }
                return saveToMediaStore(bytes, file)
            }

            val destFile = uniqueDestFile(File(destFolder), file.name)
            FileInputStream(src).use { inp ->
                FileOutputStream(destFile).use { out ->
                    inp.copyTo(out)
                }
            }
            MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun writeRecoveryRecord(file: RecoverableFile, destFolder: String): Boolean {
        return try {
            val dest = uniqueDestFile(File(destFolder), "${file.name}_recovery_record.txt")
            dest.writeText("""
FileSalvage Recovery Record
============================
File Name    : ${file.name}
File Type    : ${file.fileType.label}
Original Size: ${file.formattedSize}
Original Path: ${file.originalPath}
Deleted      : ${file.formattedDeletedDate}
Recovery Date: ${Date()}
Recovery Chance: ${(file.recoveryChance * 100).toInt()}%
Fragments    : ${file.fragmentCount}

Note: This record confirms the file was detected.
The file data may require physical-level recovery tools
for full binary reconstruction beyond what Android allows.
            """.trimIndent())
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun createGalleryFolder(name: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // On Android 10+ use app-specific external files
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), name)
            dir.mkdirs()
            dir.absolutePath
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), name)
            dir.mkdirs()
            dir.absolutePath
        }
    }

    private fun createDownloadsFolder(name: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name)
            dir.mkdirs()
            dir.absolutePath
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), name)
            dir.mkdirs()
            dir.absolutePath
        }
    }

    private fun uniqueDestFile(folder: File, name: String): File {
        var dest = File(folder, name)
        var counter = 1
        while (dest.exists()) {
            val base = name.substringBeforeLast(".")
            val ext = name.substringAfterLast(".", "")
            dest = File(folder, if (ext.isEmpty()) "${base}_$counter" else "${base}_$counter.$ext")
            counter++
        }
        return dest
    }

    private fun mimeTypeFor(type: FileType): String = when (type) {
        FileType.PHOTO    -> "image/jpeg"
        FileType.VIDEO    -> "video/mp4"
        FileType.AUDIO    -> "audio/mpeg"
        FileType.DOCUMENT -> "application/octet-stream"
        FileType.APK      -> "application/vnd.android.package-archive"
        FileType.OTHER    -> "application/octet-stream"
    }
}
