package com.filesalvage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.filesalvage.models.*
import com.filesalvage.ui.screens.*
import com.filesalvage.ui.theme.FileSalvageTheme
import com.filesalvage.viewmodels.ScanViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FileSalvageTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0000)) {
                    FileSalvageApp(viewModel)
                }
            }
        }
    }
}

@Composable
fun FileSalvageApp(viewModel: ScanViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val files by viewModel.filteredFiles.collectAsState()

    var showDestDialog by remember { mutableStateOf(false) }

    when {
        // Recovery in progress or done
        uiState.isRecovering || uiState.recoveryDone -> {
            RecoveryScreen(
                progress = uiState.recoveryProgress,
                isDone = uiState.recoveryDone,
                successCount = uiState.recoverySuccessCount,
                destination = RecoveryDestination.GALLERY,
                onChooseDestination = {},
                onDismiss = {
                    viewModel.dismissRecoveryDone()
                }
            )
        }

        // Scanning in progress
        uiState.scanState == ScanState.SCANNING -> {
            ScanningScreen(
                progress = uiState.progress,
                scanType = uiState.scanResult?.scanType ?: ScanType.QUICK
            )
        }

        // Results ready
        uiState.scanState == ScanState.COMPLETED -> {
            ResultsScreen(
                files = files,
                selectedFiles = uiState.selectedFiles,
                filterType = uiState.filterType,
                onToggleSelect = viewModel::toggleFileSelection,
                onSelectAll = viewModel::selectAll,
                onFilter = viewModel::setFilter,
                onRecover = { showDestDialog = true },
                onReset = viewModel::reset
            )

            if (showDestDialog) {
                RecoveryDestinationDialog(
                    fileCount = uiState.selectedFiles.size,
                    onConfirm = { dest ->
                        showDestDialog = false
                        viewModel.recoverSelected(dest)
                    },
                    onDismiss = { showDestDialog = false }
                )
            }
        }

        // Home / idle
        else -> {
            HomeScreen(
                onStartScan = { scanType ->
                    viewModel.startScanWithResults(scanType)
                }
            )
        }
    }
}
