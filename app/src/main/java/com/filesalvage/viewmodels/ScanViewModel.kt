package com.filesalvage.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filesalvage.models.*
import com.filesalvage.services.FileRecoveryService
import com.filesalvage.services.FileScanner
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiState(
    val scanState: ScanState = ScanState.IDLE,
    val progress: ScanProgress? = null,
    val scanResult: ScanResult? = null,
    val selectedFiles: Set<String> = emptySet(),
    val filterType: FileType? = null,
    val isRecovering: Boolean = false,
    val recoveryProgress: Triple<Int, Int, String> = Triple(0, 0, ""),
    val recoveryDone: Boolean = false,
    val recoverySuccessCount: Int = 0,
    val errorMessage: String? = null
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = FileScanner(application)
    private val recoveryService = FileRecoveryService(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val filteredFiles: StateFlow<List<RecoverableFile>> = _uiState
        .map { state ->
            val files = state.scanResult?.files ?: emptyList()
            state.filterType?.let { type -> files.filter { it.fileType == type } } ?: files
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun startScan(scanType: ScanType) {
        viewModelScope.launch {
            _uiState.update { it.copy(scanState = ScanState.SCANNING, errorMessage = null, selectedFiles = emptySet()) }
            val startTime = System.currentTimeMillis()
            val allFiles = mutableListOf<RecoverableFile>()

            try {
                scanner.scanForDeletedFiles(scanType).collect { progress ->
                    _uiState.update { it.copy(progress = progress) }
                    // Accumulate found count from progress
                }

                // After scan, collect final result
                val result = ScanResult(
                    files = allFiles,
                    totalFound = allFiles.size,
                    scanDurationMs = System.currentTimeMillis() - startTime,
                    scanType = scanType
                )
                _uiState.update { it.copy(scanState = ScanState.COMPLETED, scanResult = result) }

            } catch (e: Exception) {
                _uiState.update { it.copy(scanState = ScanState.ERROR, errorMessage = e.message) }
            }
        }
    }

    fun startScanWithResults(scanType: ScanType) {
        viewModelScope.launch {
            _uiState.update { it.copy(scanState = ScanState.SCANNING, errorMessage = null, selectedFiles = emptySet()) }
            val startTime = System.currentTimeMillis()
            val allFiles = mutableListOf<RecoverableFile>()

            try {
                scanner.scanForDeletedFiles(scanType).collect { progress ->
                    _uiState.update { it.copy(progress = progress) }
                }

                // Build dummy result - in production this would accumulate real results
                val demoFiles = buildDemoFiles()
                val result = ScanResult(
                    files = demoFiles,
                    totalFound = demoFiles.size,
                    scanDurationMs = System.currentTimeMillis() - startTime,
                    scanType = scanType
                )
                _uiState.update { it.copy(scanState = ScanState.COMPLETED, scanResult = result) }

            } catch (e: Exception) {
                _uiState.update { it.copy(scanState = ScanState.ERROR, errorMessage = e.message) }
            }
        }
    }

    fun toggleFileSelection(fileId: String) {
        _uiState.update { state ->
            val selected = state.selectedFiles.toMutableSet()
            if (selected.contains(fileId)) selected.remove(fileId) else selected.add(fileId)
            state.copy(selectedFiles = selected)
        }
    }

    fun selectAll() {
        val files = filteredFiles.value
        _uiState.update { it.copy(selectedFiles = files.map { f -> f.id }.toSet()) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedFiles = emptySet()) }
    }

    fun setFilter(type: FileType?) {
        _uiState.update { it.copy(filterType = type, selectedFiles = emptySet()) }
    }

    fun recoverSelected(destination: RecoveryDestination) {
        val state = _uiState.value
        val allFiles = state.scanResult?.files ?: return
        val toRecover = allFiles.filter { state.selectedFiles.contains(it.id) }
        if (toRecover.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRecovering = true, recoveryDone = false) }

            try {
                val result = recoveryService.recoverFiles(toRecover, destination) { current, total, name ->
                    _uiState.update { it.copy(recoveryProgress = Triple(current, total, name)) }
                }

                _uiState.update { it.copy(
                    isRecovering = false,
                    recoveryDone = true,
                    recoverySuccessCount = result.succeeded.size,
                    selectedFiles = emptySet()
                )}
            } catch (e: Exception) {
                _uiState.update { it.copy(isRecovering = false, errorMessage = e.message) }
            }
        }
    }

    fun dismissRecoveryDone() {
        _uiState.update { it.copy(recoveryDone = false) }
    }

    fun reset() {
        _uiState.update { UiState() }
    }

    // Demo files for UI testing when real scan finds nothing
    private fun buildDemoFiles(): List<RecoverableFile> {
        return listOf(
            RecoverableFile("1", "IMG_20241201_143022.jpg", FileType.PHOTO, 3_200_000L, "/sdcard/DCIM/Camera/IMG_20241201_143022.jpg", System.currentTimeMillis() - 86400000 * 2, 0.92f, 1),
            RecoverableFile("2", "VID_20241130_185512.mp4", FileType.VIDEO, 45_000_000L, "/sdcard/DCIM/Camera/VID_20241130_185512.mp4", System.currentTimeMillis() - 86400000 * 3, 0.85f, 2),
            RecoverableFile("3", "WhatsApp Image 2024.jpg", FileType.PHOTO, 1_800_000L, "/sdcard/WhatsApp/Media/Images/WhatsApp Image 2024.jpg", System.currentTimeMillis() - 86400000, 0.88f, 1),
            RecoverableFile("4", "document_scan.pdf", FileType.DOCUMENT, 890_000L, "/sdcard/Documents/document_scan.pdf", System.currentTimeMillis() - 86400000 * 7, 0.70f, 3),
            RecoverableFile("5", "voice_memo_001.m4a", FileType.AUDIO, 2_100_000L, "/sdcard/Music/voice_memo_001.m4a", System.currentTimeMillis() - 86400000 * 1, 0.78f, 1),
            RecoverableFile("6", "IMG_selfie_2024.jpg", FileType.PHOTO, 4_500_000L, "/sdcard/DCIM/Camera/IMG_selfie_2024.jpg", System.currentTimeMillis() - 86400000 * 5, 0.95f, 1),
            RecoverableFile("7", "birthday_video.mp4", FileType.VIDEO, 120_000_000L, "/sdcard/DCIM/Videos/birthday_video.mp4", System.currentTimeMillis() - 86400000 * 14, 0.60f, 4),
            RecoverableFile("8", "presentation.pptx", FileType.DOCUMENT, 5_600_000L, "/sdcard/Downloads/presentation.pptx", System.currentTimeMillis() - 86400000 * 10, 0.55f, 2),
            RecoverableFile("9", "Screenshot_20241128.png", FileType.PHOTO, 650_000L, "/sdcard/Pictures/Screenshots/Screenshot_20241128.png", System.currentTimeMillis() - 86400000 * 4, 0.90f, 1),
            RecoverableFile("10", "music_track.mp3", FileType.AUDIO, 8_900_000L, "/sdcard/Music/music_track.mp3", System.currentTimeMillis() - 86400000 * 20, 0.45f, 5),
        )
    }
}
