package com.williamchandra.kettoryn.ui.share

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.williamchandra.kettoryn.data.local.SessionWithExercises
import com.williamchandra.kettoryn.data.preferences.WeightUnit
import com.williamchandra.kettoryn.ui.theme.KettorynTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutShareSheet(
    sessionDetail: SessionWithExercises,
    weightUnit: WeightUnit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultDark = KettorynTheme.colors.isDark
    var template by rememberSaveable { mutableStateOf(ShareTemplate.SUMMARY) }
    var darkCard by rememberSaveable { mutableStateOf(defaultDark) }
    var showTitle by rememberSaveable { mutableStateOf(true) }
    var showVolume by rememberSaveable { mutableStateOf(true) }
    var showExercises by rememberSaveable { mutableStateOf(true) }
    var showMuscles by rememberSaveable { mutableStateOf(true) }
    var storyPreview by rememberSaveable { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    var privacyOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var bitmapToSave by remember { mutableStateOf<Bitmap?>(null) }
    val options = WorkoutShareOptions(template, darkCard, showTitle, showVolume, showExercises, showMuscles)
    val rendered by key(sessionDetail, weightUnit, options) {
        produceState<Result<Bitmap>?>(null) {
            value = withContext(Dispatchers.Default) {
                runCatching { WorkoutShareRenderer.renderShareCardBitmap(sessionDetail, weightUnit, options, context) }
            }
        }
    }
    val bitmap = rendered?.getOrNull()
    val story by key(bitmap, darkCard) {
        produceState<Bitmap?>(null) {
            value = bitmap?.let { withContext(Dispatchers.Default) { WorkoutShareRenderer.renderStoryBitmap(it, darkCard) } }
        }
    }
    val displayedBitmap = if (storyPreview) story else bitmap

    val saveImage = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val image = bitmapToSave
        bitmapToSave = null
        if (uri != null && image != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Could not open destination.")
                    WorkoutShareRenderer.writeBitmapToOutputStream(image, output)
                }
            }
            if (result.isSuccess) Toast.makeText(context, "Workout card saved", Toast.LENGTH_SHORT).show()
            else actionError = "Could not save image. Try another location."
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Share workout", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { darkCard = !darkCard }) {
                    Icon(Icons.Default.Brightness6, "Use ${if (darkCard) "light" else "dark"} card")
                }
                Box {
                    IconButton(onClick = { moreExpanded = true }) { Icon(Icons.Default.MoreVert, "Share options") }
                    DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Save PNG") },
                            enabled = displayedBitmap != null && !busy,
                            onClick = {
                                moreExpanded = false
                                bitmapToSave = displayedBitmap
                                saveImage.launch(if (storyPreview) "kettoryn-story.png" else "kettoryn-workout.png")
                            }
                        )
                        DropdownMenuItem(text = { Text("Privacy") }, onClick = {
                            moreExpanded = false
                            privacyOpen = true
                        })
                    }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close share preview") }
            }
            Row(Modifier.fillMaxWidth()) {
                ShareTemplate.entries.forEach { item ->
                    val selected=template==item
                    TextButton(onClick={ template=item },modifier=Modifier.weight(1f).heightIn(min=56.dp)) {
                        Text(item.label, style=MaterialTheme.typography.bodyLarge,
                            color=if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration=if(selected) androidx.compose.ui.text.style.TextDecoration.Underline else androidx.compose.ui.text.style.TextDecoration.None)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Story preview", modifier = Modifier.weight(1f))
                Switch(storyPreview, { storyPreview = it }, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Story preview format" },
                    thumbContent = { Box(Modifier.size(16.dp)) },
                    colors = SwitchDefaults.colors(uncheckedThumbColor = MaterialTheme.colorScheme.onSurface, uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant, uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant))
            }
            Box(
                Modifier.fillMaxWidth().aspectRatio(displayedBitmap?.let { it.width.toFloat()/it.height } ?: if (storyPreview) 1080f/1920f else 3f/2f),
                contentAlignment = Alignment.Center
            ) {
                if (displayedBitmap != null) {
                    Image(
                        displayedBitmap.asImageBitmap(),
                        "${template.label} card preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else if (rendered?.isFailure == true) {
                    Text("Could not create preview", color = MaterialTheme.colorScheme.error)
                } else CircularProgressIndicator(Modifier.size(28.dp))
            }
            if (actionError != null) {
                Text(actionError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                enabled = displayedBitmap != null && !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = MaterialTheme.shapes.small,
                onClick = {
                    val image = displayedBitmap ?: return@Button
                    busy = true
                    scope.launch {
                        val result = runCatching {
                            WorkoutShareRenderer.shareViaSystemSheet(context, image, if (showTitle) sessionDetail.session.title else "Workout")
                        }
                        busy = false
                        actionError = if (result.isFailure) "No sharing app available." else null
                    }
                }
            ) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (storyPreview) "Share story" else "Share image")
            }
            OutlinedButton(
                enabled = displayedBitmap != null && !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = MaterialTheme.shapes.small,
                onClick = {
                    val image = bitmap ?: return@OutlinedButton
                    busy = true
                    scope.launch {
                        val result = runCatching {
                            WorkoutShareRenderer.shareToInstagramStoryOrFallback(
                                context, image, if (showTitle) sessionDetail.session.title else "Workout", darkCard
                            )
                        }
                        busy = false
                        actionError = if (result.isFailure) "Could not open a sharing app." else null
                        if (result.getOrNull() == false) Toast.makeText(context, "Opened Android sharing", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                InstagramGlyph()
                Spacer(Modifier.width(8.dp))
                Text("Instagram Story")
            }
        }
    }
    if (privacyOpen) {
        AlertDialog(
            onDismissRequest = { privacyOpen = false },
            title = { Text("Privacy") },
            text = {
                Column {
                    ShareOptionRow("Workout title", showTitle) { showTitle = it }
                    ShareOptionRow("Muscles trained", showMuscles) { showMuscles = it }
                    if (template == ShareTemplate.EXERCISES) ShareOptionRow("Exercise list", showExercises) { showExercises = it }
                    if (template == ShareTemplate.SUMMARY) {
                        ShareOptionRow("Total volume", showVolume) { showVolume = it }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { privacyOpen = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun ShareOptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun InstagramGlyph() {
    val tint=MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val w=size.width;val h=size.height
        val stroke=androidx.compose.ui.graphics.drawscope.Stroke(w*0.085f)
        drawRoundRect(tint,androidx.compose.ui.geometry.Offset(w*0.08f,h*0.08f),
            androidx.compose.ui.geometry.Size(w*0.84f,h*0.84f),androidx.compose.ui.geometry.CornerRadius(w*0.23f),style=stroke)
        drawCircle(tint,w*0.2f,androidx.compose.ui.geometry.Offset(w*0.5f,h*0.5f),style=stroke)
        drawCircle(tint,w*0.055f,androidx.compose.ui.geometry.Offset(w*0.73f,h*0.27f))
    }
}
