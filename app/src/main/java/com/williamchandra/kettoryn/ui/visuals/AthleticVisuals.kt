package com.williamchandra.kettoryn.ui.visuals

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.williamchandra.kettoryn.R
import com.williamchandra.kettoryn.ui.theme.KettorynTheme

@Composable
fun ExerciseArt(resource: Int?, modifier: Modifier = Modifier, description: String? = null) {
    if (resource == null) return
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color(0xFF191E26)) {
        Image(painterResource(resource), description, Modifier.padding(4.dp), contentScale = ContentScale.Fit)
    }
}

@Composable
fun ContextualCreateFAB(label: String, onClick: () -> Unit) {
    FloatingActionButton(onClick, shape = RoundedCornerShape(16.dp), containerColor = MaterialTheme.colorScheme.primary) {
        Icon(Icons.Default.Add, label)
    }
}


/** A custom photo has no implied exercise identity; an absent photo has a neutral equipment placeholder. */
@Composable
fun CustomExerciseImage(key: String?, modifier: Modifier = Modifier, description: String? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, key) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            key?.let { CustomPhotoCache.load(context, it) }
        }
    }
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = KettorynTheme.colors.mutedSurface) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap!!.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Default.FitnessCenter, description ?: "No exercise photo", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private object CustomPhotoCache {
    private val cache = object : android.util.LruCache<String, android.graphics.Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap): Int = value.allocationByteCount
    }
    fun load(context: android.content.Context, key: String): android.graphics.Bitmap? {
        cache.get(key)?.let { return it }
        val file = com.williamchandra.kettoryn.data.media.CustomExerciseMediaStore(context).file(key) ?: return null
        val decoded = runCatching { android.graphics.BitmapFactory.decodeFile(file.path) }.getOrNull() ?: return null
        cache.put(key, decoded)
        return decoded
    }
}
