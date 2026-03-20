package com.filesalvage.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.filesalvage.models.*
import com.filesalvage.ui.theme.*
import com.filesalvage.viewmodels.*
import kotlin.math.*

// ─── HOME SCREEN ─────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(onStartScan: (ScanType) -> Unit) {
    var radarAngle by remember { mutableStateOf(0f) }
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val animatedAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "angle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        // Background radar canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height * 0.38f
            val maxR = size.width * 0.45f

            // Concentric rings
            for (i in 1..4) {
                drawCircle(
                    color = RedPrimary.copy(alpha = 0.06f),
                    radius = maxR * i / 4,
                    center = Offset(cx, cy),
                    style = Stroke(1f)
                )
            }
            // Crosshairs
            drawLine(RedPrimary.copy(alpha = 0.05f), Offset(cx - maxR, cy), Offset(cx + maxR, cy), 1f)
            drawLine(RedPrimary.copy(alpha = 0.05f), Offset(cx, cy - maxR), Offset(cx, cy + maxR), 1f)

            // Radar sweep
            val sweepRad = Math.toRadians(animatedAngle.toDouble())
            rotate(animatedAngle, pivot = Offset(cx, cy)) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(RedPrimary.copy(alpha = 0f), RedPrimary.copy(alpha = 0.3f)),
                        center = Offset(cx, cy)
                    ),
                    startAngle = -90f, sweepAngle = 60f,
                    useCenter = true,
                    topLeft = Offset(cx - maxR, cy - maxR),
                    size = Size(maxR * 2, maxR * 2)
                )
                drawLine(
                    RedPrimary.copy(alpha = 0.6f),
                    Offset(cx, cy),
                    Offset(cx + maxR * cos(Math.toRadians(-90.0)).toFloat(),
                           cy + maxR * sin(Math.toRadians(-90.0)).toFloat()),
                    strokeWidth = 1.5f
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(80.dp))

            // App icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(RedGlow, CircleShape)
                    .border(1.dp, RedPrimary.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Search, contentDescription = null,
                    tint = RedPrimary, modifier = Modifier.size(48.dp))
            }

            Spacer(Modifier.height(24.dp))

            Text("FileSalvage",
                fontSize = 32.sp, fontWeight = FontWeight.Black, color = TextPrimary,
                letterSpacing = 1.sp)

            Text("Android Data Recovery",
                fontSize = 14.sp, color = RedPrimary.copy(alpha = 0.7f),
                letterSpacing = 2.sp, modifier = Modifier.padding(top = 4.dp))

            Spacer(Modifier.height(48.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatPill("Photos", Icons.Filled.PhotoLibrary, Modifier.weight(1f))
                StatPill("Videos", Icons.Filled.VideoLibrary, Modifier.weight(1f))
                StatPill("Docs",   Icons.Filled.Description,  Modifier.weight(1f))
            }

            Spacer(Modifier.height(48.dp))

            // Quick scan button
            Button(
                onClick = { onStartScan(ScanType.QUICK) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                shape = RoundedCornerShape(18.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Icon(Icons.Filled.FlashOn, contentDescription = null,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Quick Scan", fontSize = 17.sp, fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp)
            }

            Spacer(Modifier.height(12.dp))

            // Deep scan button
            OutlinedButton(
                onClick = { onStartScan(ScanType.DEEP) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RedPrimary),
                border = BorderStroke(1.dp, RedPrimary.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(Icons.Filled.Radar, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Deep Scan (Thorough)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(40.dp))

            // Feature list
            FeaturesList()

            Spacer(Modifier.height(60.dp))
        }
    }
}

@Composable
fun StatPill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(BgCard, RoundedCornerShape(14.dp))
            .border(1.dp, BgCardBorder, RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = RedPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FeaturesList() {
    val features = listOf(
        Triple(Icons.Filled.PhotoLibrary, "Photos & Videos", "JPEG, PNG, MP4, MOV and more"),
        Triple(Icons.Filled.AudioFile,    "Audio Files",     "MP3, M4A, WAV, FLAC"),
        Triple(Icons.Filled.Description,  "Documents",       "PDF, DOC, XLS, ZIP"),
        Triple(Icons.Filled.Android,      "WhatsApp Media",  "Images, videos, voice notes"),
        Triple(Icons.Filled.SdCard,       "SD Card Support", "External storage scanning"),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .background(BgCard, RoundedCornerShape(20.dp))
            .border(1.dp, BgCardBorder, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("What can be recovered", fontSize = 13.sp,
            fontWeight = FontWeight.Black, color = RedPrimary, letterSpacing = 1.sp)
        features.forEach { (icon, title, subtitle) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(RedGlow, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null,
                        tint = RedPrimary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(subtitle, fontSize = 12.sp, color = TextSecondary)
                }
            }
        }
    }
}

// ─── SCANNING SCREEN ─────────────────────────────────────────────────────────

@Composable
fun ScanningScreen(progress: ScanProgress?, scanType: ScanType) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val rotation by infiniteTransition.animateFloat(
        0f, 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "rot"
    )
    val pulse by infiniteTransition.animateFloat(
        0.9f, 1.1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(BgDeep),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Animated scan rings
            Box(contentAlignment = Alignment.Center) {
                for (i in 3 downTo 1) {
                    Box(
                        modifier = Modifier
                            .size((80 + i * 50).dp)
                            .border(
                                1.dp,
                                RedPrimary.copy(alpha = 0.06f * i),
                                CircleShape
                            )
                    )
                }
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .scale(pulse)
                        .background(RedGlow, CircleShape)
                        .border(2.dp, RedPrimary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(80.dp).rotate(rotation),
                        color = RedPrimary,
                        strokeWidth = 2.dp,
                        trackColor = RedPrimary.copy(alpha = 0.1f)
                    )
                    Icon(Icons.Filled.Search, contentDescription = null,
                        tint = RedPrimary, modifier = Modifier.size(36.dp))
                }
            }

            Spacer(Modifier.height(40.dp))

            Text(
                if (scanType == ScanType.DEEP) "Deep Scanning..." else "Quick Scanning...",
                fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary
            )

            Spacer(Modifier.height(8.dp))

            Text(
                progress?.step ?: "Initializing...",
                fontSize = 14.sp, color = RedPrimary.copy(alpha = 0.8f)
            )

            Spacer(Modifier.height(32.dp))

            // Progress bar
            val prog = progress?.percent ?: 0f
            Column(
                modifier = Modifier.width(280.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(RedPrimary.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(prog.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(listOf(RedDark, RedPrimary, RedAccent))
                            )
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${(prog * 100).toInt()}%", fontSize = 12.sp, color = RedPrimary, fontWeight = FontWeight.Bold)
                    Text("${progress?.filesFound ?: 0} found", fontSize = 12.sp, color = TextSecondary)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Step indicator
            progress?.let {
                Text("Step ${it.stepIndex + 1} of ${it.totalSteps}",
                    fontSize = 12.sp, color = TextMuted)
            }
        }
    }
}

// ─── RESULTS SCREEN ──────────────────────────────────────────────────────────

@Composable
fun ResultsScreen(
    files: List<RecoverableFile>,
    selectedFiles: Set<String>,
    filterType: FileType?,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onFilter: (FileType?) -> Unit,
    onRecover: () -> Unit,
    onReset: () -> Unit
) {
    val groupedCounts = remember(files) {
        FileType.values().associateWith { type -> files.count { it.fileType == type } }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgDeep)) {

        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(BgCard, BgDeep))
                )
                .padding(start = 24.dp, end = 24.dp, top = 56.dp, bottom = 16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Scan Complete", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                        Text("${files.size} recoverable files found",
                            fontSize = 14.sp, color = RedPrimary.copy(alpha = 0.8f))
                    }
                    TextButton(onClick = onReset) {
                        Text("New Scan", color = RedPrimary, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Filter chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip("All", null, filterType == null,
                            files.size.toString(), onFilter)
                    }
                    items(FileType.values().filter { (groupedCounts[it] ?: 0) > 0 }) { type ->
                        FilterChip(
                            type.label, type, filterType == type,
                            (groupedCounts[type] ?: 0).toString(), onFilter
                        )
                    }
                }
            }
        }

        // Select all bar
        if (files.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${selectedFiles.size} selected",
                    fontSize = 13.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                TextButton(onClick = onSelectAll) {
                    Text("Select All", color = RedPrimary, fontSize = 13.sp)
                }
            }
        }

        // File list
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(files, key = { it.id }) { file ->
                FileCard(
                    file = file,
                    isSelected = selectedFiles.contains(file.id),
                    onToggle = { onToggleSelect(file.id) }
                )
            }
            item { Spacer(Modifier.height(100.dp)) }
        }

        // Bottom bar
        if (selectedFiles.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = BgCard,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${selectedFiles.size} files selected",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        val totalSize = files.filter { selectedFiles.contains(it.id) }.sumOf { it.size }
                        Text(formatSize(totalSize), fontSize = 12.sp, color = TextSecondary)
                    }
                    Button(
                        onClick = onRecover,
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Recover", fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChip(
    label: String, type: FileType?, isSelected: Boolean,
    count: String, onFilter: (FileType?) -> Unit
) {
    val bg = if (isSelected) RedPrimary else BgCard
    val textColor = if (isSelected) Color.White else TextSecondary

    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(20.dp))
            .border(1.dp, if (isSelected) RedPrimary else BgCardBorder, RoundedCornerShape(20.dp))
            .clickable { onFilter(type) }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor)
            Spacer(Modifier.width(5.dp))
            Text(count, fontSize = 11.sp, color = textColor.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun FileCard(file: RecoverableFile, isSelected: Boolean, onToggle: () -> Unit) {
    val borderColor = if (isSelected) RedPrimary else BgCardBorder
    val bg = if (isSelected) RedPrimary.copy(alpha = 0.08f) else BgCard

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onToggle() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    if (isSelected) RedPrimary else Color.Transparent,
                    CircleShape
                )
                .border(1.5.dp, if (isSelected) RedPrimary else TextMuted, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(Icons.Filled.Check, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }

        Spacer(Modifier.width(12.dp))

        // File type icon
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(fileTypeColor(file.fileType).copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                fileTypeIcon(file.fileType), contentDescription = null,
                tint = fileTypeColor(file.fileType), modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(file.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(file.formattedSize, fontSize = 11.sp, color = TextSecondary)
                Text("•", fontSize = 11.sp, color = TextMuted)
                Text(file.formattedDeletedDate, fontSize = 11.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(5.dp))
            // Recovery chance bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(RedPrimary.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(file.recoveryChance)
                            .fillMaxHeight()
                            .background(recoveryColor(file.recoveryChance))
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("${(file.recoveryChance * 100).toInt()}%",
                    fontSize = 10.sp, color = recoveryColor(file.recoveryChance),
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── RECOVERY SCREEN ─────────────────────────────────────────────────────────

@Composable
fun RecoveryScreen(
    progress: Triple<Int, Int, String>,
    isDone: Boolean,
    successCount: Int,
    destination: RecoveryDestination,
    onChooseDestination: (RecoveryDestination) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(BgDeep),
        contentAlignment = Alignment.Center
    ) {
        if (!isDone) {
            // Recovery in progress
            val (current, total, name) = progress
            val percent = if (total > 0) current.toFloat() / total else 0f

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val infiniteTransition = rememberInfiniteTransition(label = "rec")
                val rotation by infiniteTransition.animateFloat(
                    0f, 360f,
                    animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
                    label = "r"
                )

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(RedGlow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { percent },
                        modifier = Modifier.size(90.dp).rotate(rotation),
                        color = RedPrimary,
                        strokeWidth = 4.dp,
                        trackColor = RedPrimary.copy(alpha = 0.1f)
                    )
                    Icon(Icons.Filled.Restore, contentDescription = null,
                        tint = RedPrimary, modifier = Modifier.size(40.dp))
                }

                Spacer(Modifier.height(32.dp))
                Text("Recovering Files...", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(name, fontSize = 14.sp, color = RedPrimary.copy(alpha = 0.8f), maxLines = 1)
                Spacer(Modifier.height(8.dp))
                Text("$current of $total files", fontSize = 13.sp, color = TextSecondary)
            }

        } else {
            // Done
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(Success.copy(alpha = 0.15f), CircleShape)
                        .border(2.dp, Success.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null,
                        tint = Success, modifier = Modifier.size(56.dp))
                }

                Spacer(Modifier.height(24.dp))
                Text("Recovery Complete!", fontSize = 24.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                Text("$successCount files recovered successfully",
                    fontSize = 15.sp, color = Success, fontWeight = FontWeight.SemiBold)

                Spacer(Modifier.height(12.dp))
                Text(
                    when (destination) {
                        RecoveryDestination.GALLERY   -> "Saved to Photos Gallery"
                        RecoveryDestination.DOWNLOADS -> "Saved to Downloads folder"
                        else -> "Saved to selected location"
                    },
                    fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(40.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Done", fontSize = 17.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

// ─── RECOVERY DESTINATION DIALOG ─────────────────────────────────────────────

@Composable
fun RecoveryDestinationDialog(
    fileCount: Int,
    onConfirm: (RecoveryDestination) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(RecoveryDestination.GALLERY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        title = {
            Text("Recover $fileCount files", color = TextPrimary, fontWeight = FontWeight.Black)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose where to save recovered files:",
                    fontSize = 14.sp, color = TextSecondary)

                listOf(
                    Triple(RecoveryDestination.GALLERY, "Photos Gallery",
                        "Saved directly to your photo/video gallery"),
                    Triple(RecoveryDestination.DOWNLOADS, "Downloads Folder",
                        "Saved to your Downloads folder")
                ).forEach { (dest, title, subtitle) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected == dest) RedPrimary.copy(alpha = 0.1f)
                                else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                if (selected == dest) RedPrimary.copy(alpha = 0.4f)
                                else BgCardBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selected = dest }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == dest,
                            onClick = { selected = dest },
                            colors = RadioButtonDefaults.colors(selectedColor = RedPrimary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(subtitle, fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selected) },
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Recovery", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

// ─── HELPERS ─────────────────────────────────────────────────────────────────

fun fileTypeIcon(type: FileType) = when (type) {
    FileType.PHOTO    -> Icons.Filled.PhotoLibrary
    FileType.VIDEO    -> Icons.Filled.VideoLibrary
    FileType.AUDIO    -> Icons.Filled.AudioFile
    FileType.DOCUMENT -> Icons.Filled.Description
    FileType.APK      -> Icons.Filled.Android
    FileType.OTHER    -> Icons.Filled.InsertDriveFile
}

fun fileTypeColor(type: FileType) = when (type) {
    FileType.PHOTO    -> Color(0xFF147EFB)
    FileType.VIDEO    -> Color(0xFFAF52DE)
    FileType.AUDIO    -> Color(0xFF34C759)
    FileType.DOCUMENT -> Color(0xFFFF9500)
    FileType.APK      -> Color(0xFF00C7BE)
    FileType.OTHER    -> Color(0xFF8E8E93)
}

fun recoveryColor(chance: Float) = when {
    chance >= 0.8f -> Color(0xFF34C759)
    chance >= 0.5f -> Color(0xFFFF9500)
    else           -> Color(0xFFFF3B30)
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000     -> String.format("%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000         -> String.format("%.1f KB", bytes / 1_000.0)
    else                   -> "$bytes B"
}
