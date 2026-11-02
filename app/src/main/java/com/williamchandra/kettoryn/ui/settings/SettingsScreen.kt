package com.williamchandra.kettoryn.ui.settings

import androidx.compose.material3.HorizontalDivider
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.williamchandra.kettoryn.data.backup.BackupManager
import com.williamchandra.kettoryn.data.backup.StagedBackup
import com.williamchandra.kettoryn.data.preferences.ThemeMode
import com.williamchandra.kettoryn.data.preferences.UserPreferences
import com.williamchandra.kettoryn.data.preferences.WeightUnit
import com.williamchandra.kettoryn.domain.UnitConverter
import com.williamchandra.kettoryn.ui.components.ErrorStateBanner
import com.williamchandra.kettoryn.ui.components.KettorynPanel
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val BackupDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy • HH:mm", Locale.US)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onWeightUnitChange: (WeightUnit) -> Unit,
    onDefaultRestSecondsChange: (Int) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onExportBackup: (OutputStream, (Result<Unit>) -> Unit) -> Unit,
    onInspectBackup: (InputStream, (Result<StagedBackup>) -> Unit) -> Unit,
    onApplyStagedBackup: (StagedBackup, (Result<Unit>) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var showIllustrationCredits by rememberSaveable { mutableStateOf(false) }
    var showFullNotices by rememberSaveable { mutableStateOf(false) }
    var restInputText by rememberSaveable(preferences.defaultRestSeconds) {
        mutableStateOf(preferences.defaultRestSeconds.toString())
    }
    LaunchedEffect(preferences.defaultRestSeconds) {
        restInputText = preferences.defaultRestSeconds.toString()
    }
    val parsedRestSeconds = UnitConverter.parseRestSeconds(restInputText)
    val isRestValid = parsedRestSeconds != null

    var statusBanner by rememberSaveable { mutableStateOf<String?>(null) }
    var errorBanner by rememberSaveable { mutableStateOf<String?>(null) }
    var stagedBackupForConfirm by remember { mutableStateOf<StagedBackup?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            errorBanner = null
            val stream = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
            if (stream == null) {
                errorBanner = "Could not open destination file for backup."
            } else {
                onExportBackup(stream) { result ->
                    result.onSuccess {
                        statusBanner = "Backup exported successfully."
                        Toast.makeText(context, "Backup exported.", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        errorBanner = err.message ?: "Failed to export backup."
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            errorBanner = null
            statusBanner = null
            val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            if (stream == null) {
                errorBanner = "Could not read selected backup file."
            } else {
                onInspectBackup(stream) { result ->
                    result.onSuccess { staged ->
                        stagedBackupForConfirm = staged
                    }.onFailure { err ->
                        errorBanner = err.message ?: "Selected file is not a valid Kettoryn backup."
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (errorBanner != null) {
                ErrorStateBanner(
                    message = errorBanner!!,
                    onDismissOrRetry = { errorBanner = null }
                )
            }

            if (statusBanner != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusBanner!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { statusBanner = null }) {
                            Text("OK")
                        }
                    }
                }
            }

            // 1. Appearance (Dark / Light / System)
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(mode.label, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            // 2. Units (Weight kg/lb + Length cm/in)
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Training units",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Weight Unit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WeightUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = preferences.weightUnit == unit,
                                onClick = { onWeightUnitChange(unit) },
                                label = { Text(unit.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }

            }

            // 3. Manual Rest Timer in Seconds (F3)
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Rest timer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    OutlinedTextField(
                        value = restInputText,
                        onValueChange = { raw ->
                            restInputText = raw.take(12)
                        },
                        label = { Text("Default rest") },
                        suffix = { Text("s") },
                        singleLine = true,
                        isError = !isRestValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            if (parsedRestSeconds != null) {
                                val formatted = UnitConverter.formatRestSecondsHint(parsedRestSeconds)
                                Text(
                                    if (parsedRestSeconds == 0) {
                                        "Off"
                                    } else {
                                        "$parsedRestSeconds s ($formatted)"
                                    }
                                )
                            } else {
                                Text("Enter a number between 0 and 3600.")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            if (parsedRestSeconds != null) {
                                onDefaultRestSecondsChange(parsedRestSeconds)
                                Toast.makeText(
                                    context,
                                    if (parsedRestSeconds == 0) {
                                        "Default rest timer turned off."
                                    } else {
                                        "Default rest set to ${parsedRestSeconds}s (${UnitConverter.formatRestSecondsHint(parsedRestSeconds)})."
                                    },
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = isRestValid && parsedRestSeconds != preferences.defaultRestSeconds,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Save")
                    }
                }
            }

            // 4. Portable Offline Backup & Restore (F2)
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Your data",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Save or transfer your training data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            exportLauncher.launch(BackupManager.defaultBackupFileName())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Backup", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Import Backup",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text("About", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            Text(
                "Kettoryn is an offline workout tracker for planning routines and logging your training. Review your progress over time and share workout summaries, with your data stored on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { showIllustrationCredits = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Illustration credits")
            }
        }
    }

    if (showIllustrationCredits) {
        val uriHandler = LocalUriHandler.current
        val notices = remember { context.assets.open("legal/THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { showIllustrationCredits = false; showFullNotices = false },
            title = { Text(if (showFullNotices) "Legal notices" else "Illustration credits") },
            text = {
                if (showFullNotices) {
                    Text(notices, modifier = Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Workout Guide", style = MaterialTheme.typography.titleMedium)
                        Text("Exercise illustrations by Bryl Lim, derived from Everkinetic. CC BY-SA 4.0. Converted to offline PNG/WebP frames; poses and colors preserved.", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { uriHandler.openUri("https://github.com/bryllim/workout-guide") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Workout Guide source") }
                        Text("MuscleMap", style = MaterialTheme.typography.titleMedium)
                        Text("Anatomy vectors © 2026 Jsplice / MuscleMap contributors. MIT. Adapted for Android drawing and muscle highlights.", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { uriHandler.openUri("https://github.com/Jsplice/MuscleMap") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("MuscleMap source") }
                        Text("Manrope", style = MaterialTheme.typography.titleMedium)
                        Text("© 2018 The Manrope Project Authors. SIL Open Font License 1.1. Bundled as static font weights.", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { uriHandler.openUri("https://github.com/googlefonts/manrope") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Manrope source") }
                        TextButton(onClick = { showFullNotices = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Full legal notices (offline)") }
                    }
                }
            },
            dismissButton = { if (showFullNotices) TextButton(onClick = { showFullNotices = false }) { Text("Back to credits") } },
            confirmButton = { TextButton(onClick = { showIllustrationCredits = false; showFullNotices = false }) { Text("Close") } })
    }

    if (stagedBackupForConfirm != null) {
        val staged = stagedBackupForConfirm!!
        val preview = staged.preview
        val createdStr = Instant.ofEpochMilli(preview.createdAtEpochMillis)
            .atZone(ZoneId.systemDefault())
            .format(BackupDateFormatter)

        AlertDialog(
            onDismissRequest = { stagedBackupForConfirm = null },
            title = {
                Text(
                    text = "Import Backup & Replace Data?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Backup from $createdStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text("• Completed workouts: ${preview.completedWorkoutsCount}", style = MaterialTheme.typography.bodyMedium)
                    Text("• Active workout sessions: ${preview.activeWorkoutsCount}", style = MaterialTheme.typography.bodyMedium)
                    Text("• Logged sets: ${preview.loggedSetsCount}", style = MaterialTheme.typography.bodyMedium)
                    Text("• Saved routines: ${preview.templatesCount}", style = MaterialTheme.typography.bodyMedium)
                    Text("• Custom exercises: ${preview.customExercisesCount}", style = MaterialTheme.typography.bodyMedium)
                    Text("• Body measurement entries: ${preview.measurementEntriesCount}", style = MaterialTheme.typography.bodyMedium)
                    Text("• Units: ${preview.weightUnit.label} / ${preview.lengthUnit.label}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Importing replaces all workouts, routines, exercises, stored measurements, and settings on this device. This cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        stagedBackupForConfirm = null
                        onApplyStagedBackup(staged) { res ->
                            res.onSuccess {
                                statusBanner = "Backup imported (${preview.completedWorkoutsCount} workouts)."
                                Toast.makeText(context, "Backup restored successfully.", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                errorBanner = err.message ?: "Restore failed. Existing data was not modified."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Replace All Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { stagedBackupForConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
