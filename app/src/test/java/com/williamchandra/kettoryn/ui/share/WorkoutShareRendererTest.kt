package com.williamchandra.kettoryn.ui.share

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.core.content.FileProvider
import com.williamchandra.kettoryn.data.local.*
import com.williamchandra.kettoryn.data.preferences.WeightUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkoutShareRendererTest {
    @Before
    fun resetProviderCacheForTestSandbox() {
        // Robolectric changes the cache directory per test; FileProvider caches it by authority.
        val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<*, *>).clear()
    }

    private fun detail(exerciseCount: Int = 4, title: String = "Upper Body"): SessionWithExercises {
        val names = listOf("Barbell Bench Press", "Incline Dumbbell Press", "Lat Pulldown", "Seated Cable Row", "Lateral Raise", "Triceps Extension")
        return SessionWithExercises(
            WorkoutSessionEntity(id = 1, title = title, startedAt = 0, endedAt = 2_700_000, updatedAt = 2_700_000, status = SessionStatus.COMPLETED),
            (0 until exerciseCount).map { index ->
                val id = index.toLong() + 1
                SessionExerciseWithSets(
                    SessionExerciseEntity(id, 1, null, names[index % names.size], "Chest", "Barbell", index),
                    listOf(
                        LoggedSetEntity(id * 10, id, 0, 10, 50_000, true),
                        LoggedSetEntity(id * 10 + 1, id, 1, 10, 50_000, true),
                        LoggedSetEntity(id * 10 + 2, id, 2, 10, 50_000, true),
                        LoggedSetEntity(id * 10 + 3, id, 3, 10, 50_000, false),
                        LoggedSetEntity(id * 10 + 4, id, 4, 10, 20_000, true, SetType.WARMUP),
                        LoggedSetEntity(id * 10 + 5, id, 5, null, null, true)
                    )
                )
            }
        )
    }

    @Test
    fun `share totals match qualifying completed work sets without warmups or invalid rows`() {
        val content = WorkoutShareContent.from(detail(), WeightUnit.KG, WorkoutShareOptions())
        assertEquals(12, content.completedSets)
        assertEquals("6,000 kg", content.volume)
        assertEquals(listOf(3, 3, 3, 3), content.exercises.map { it.completedSets })
        assertEquals(listOf("Barbell Bench Press", "Incline Dumbbell Press", "Lat Pulldown", "Seated Cable Row"), content.exercises.map { it.name })
    }

    @Test
    fun `exercise list is limited to five without dropping totals and preserves record order`() {
        val content = WorkoutShareContent.from(detail(6), WeightUnit.KG, WorkoutShareOptions())
        assertEquals(5, content.exercises.size)
        assertEquals(1, content.remainingExercises)
        assertEquals(18, content.completedSets)
    }

    @Test
    fun `empty workouts and hidden titles have honest content`() {
        val content = WorkoutShareContent.from(detail(0), WeightUnit.KG, WorkoutShareOptions(showWorkoutTitle = false))
        assertEquals("Workout", content.title)
        assertEquals(0, content.completedSets)
        assertTrue(content.exercises.isEmpty())
        assertEquals("0 kg", content.volume)
    }

    @Test
    fun `privacy removes title and volume from actual pixels and dates never appear`() {
        val original = detail(1, "Private routine")
        val changed = original.copy(
            session = original.session.copy(title = "Another private routine", startedAt = 86_400_000, endedAt = 89_100_000),
            rawExercises = original.exercises.map { exercise ->
                exercise.copy(rawSets = exercise.sets.map { it.copy(weightGrams = 100_000) })
            }
        )
        for (template in ShareTemplate.entries) for (dark in listOf(true, false)) {
            val options = WorkoutShareOptions(template, dark, showWorkoutTitle = false, showWeightsAndVolume = false)
            val first = WorkoutShareRenderer.renderShareCardBitmap(original, WeightUnit.KG, options)
            val second = WorkoutShareRenderer.renderShareCardBitmap(changed, WeightUnit.KG, options)
            assertTrue("Hidden fields must not change exported pixels", first.sameAs(second))
            first.recycle()
            second.recycle()
        }
    }

    @Test
    fun `zero load bodyweight sets qualify while negative and unchecked rows do not`() {
        val source = detail(1)
        val exercise = source.exercises.single()
        val rows = listOf(
            LoggedSetEntity(1, 1, 0, 10, 0, true),
            LoggedSetEntity(2, 1, 1, 10, -1, true),
            LoggedSetEntity(3, 1, 2, -1, 10_000, true),
            LoggedSetEntity(4, 1, 3, 10, 10_000, false)
        )
        val content = WorkoutShareContent.from(source.copy(rawExercises = listOf(exercise.copy(rawSets = rows))), WeightUnit.KG, WorkoutShareOptions())
        assertEquals(1, content.completedSets)
        assertEquals(1, content.exercises.single().completedSets)
        assertEquals("0 kg", content.volume)
    }

    @Test
    fun `both templates and themes export the exact card pixels as PNG`() {
        val outputDir = File("build/reports/share-previews").apply { mkdirs() }
        for (template in ShareTemplate.entries) for (dark in listOf(true, false)) {
            val card = WorkoutShareRenderer.renderShareCardBitmap(detail(), WeightUnit.KG, WorkoutShareOptions(template, dark))
            val bytes = ByteArrayOutputStream()
            WorkoutShareRenderer.writeBitmapToOutputStream(card, bytes)
            val decoded = BitmapFactory.decodeByteArray(bytes.toByteArray(), 0, bytes.size())
            assertEquals(1200, decoded.width)
            assertEquals(card.height, decoded.height)
            assertTrue(card.sameAs(decoded))
            assertEquals(if (dark) 0xFF0D1015.toInt() else 0xFFF7F7F4.toInt(), card.getPixel(1168, 80))
            File(outputDir, "${template.name.lowercase()}-${if (dark) "dark" else "light"}.png").writeBytes(bytes.toByteArray())
            val story = WorkoutShareRenderer.renderStoryBitmap(card, dark)
            assertEquals(1080, story.width)
            assertEquals(1920, story.height)
            assertEquals(if (dark) 0xFF171B22.toInt() else 0xFFD5D7D9.toInt(), story.getPixel(540, 200))
            assertEquals(if (dark) 0xFF171B22.toInt() else 0xFFD5D7D9.toInt(), story.getPixel(540, 1660))
            File(outputDir, "story-${template.name.lowercase()}-${if (dark) "dark" else "light"}.png").outputStream().use {
                WorkoutShareRenderer.writeBitmapToOutputStream(story, it)
            }
        }
    }

    @Test
    fun `long titles emoji and large values remain within a bounded card`() {
        val source = detail(15, "Upper Body 🏋️ ".repeat(20))
        val long = source.copy(rawExercises = source.exercises.map {
            it.copy(
                sessionExercise = it.sessionExercise.copy(nameSnapshot = "Single Arm Dumbbell Row 🏋️ ".repeat(20)),
                rawSets = it.sets.map { set -> set.copy(weightGrams = 9_999_000) }
            )
        })
        for (template in ShareTemplate.entries) {
            val card = WorkoutShareRenderer.renderShareCardBitmap(long, WeightUnit.LB, WorkoutShareOptions(template))
            assertTrue(card.height in 800..1280)
            for (y in 60..600) {
                assertEquals("Left content gutter at y=$y", 0xFF0D1015.toInt(), card.getPixel(40, y))
                assertEquals("Right content gutter at y=$y", 0xFF0D1015.toInt(), card.getPixel(1160, y))
            }
            File("build/reports/share-previews/long-${template.name.lowercase()}.png").apply {
                parentFile!!.mkdirs()
                outputStream().use { WorkoutShareRenderer.writeBitmapToOutputStream(card, it) }
            }
        }
    }

    @Test
    fun `separate shares use distinct files and grant access to their PNG`(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val bitmap = WorkoutShareRenderer.renderShareCardBitmap(detail(), WeightUnit.KG)
        val first = WorkoutShareRenderer.saveBitmapToCacheFile(app, bitmap)
        val second = WorkoutShareRenderer.saveBitmapToCacheFile(app, bitmap)
        assertNotEquals(first.path, second.path)
        WorkoutShareRenderer.shareViaSystemSheet(app, bitmap, "Upper Body")
        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("image/png", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertNotNull(send.clipData)
        first.delete()
        second.delete()
    }

    @Test
    fun `Instagram unavailable falls back to sharing the actual portrait Story`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val card = WorkoutShareRenderer.renderShareCardBitmap(detail(), WeightUnit.KG)
        assertFalse(WorkoutShareRenderer.shareToInstagramStoryOrFallback(app, card, "Upper Body", true))
        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
        app.contentResolver.openInputStream(uri)!!.use {
            val image = BitmapFactory.decodeStream(it)
            assertEquals(1920, image.height)
        }
    }
    @Test
    fun `hidden exercise list cannot leak names into exported pixels`() {
        val original = detail(6)
        val renamed = original.copy(rawExercises = original.exercises.map {
            it.copy(sessionExercise = it.sessionExercise.copy(nameSnapshot = "PRIVATE DIFFERENT NAME"))
        })
        for (dark in listOf(true, false)) {
            val options = WorkoutShareOptions(template = ShareTemplate.EXERCISES, darkCardStyle = dark, showExerciseList = false)
            assertTrue(WorkoutShareRenderer.renderShareCardBitmap(original, WeightUnit.KG, options).sameAs(
                WorkoutShareRenderer.renderShareCardBitmap(renamed, WeightUnit.KG, options)))
        }
    }

    @Test
    fun `recorded primary groups change highlights and zero sets stay neutral`() {
        val source = detail(1)
        val differentGroup = source.copy(rawExercises = source.exercises.map {
            it.copy(sessionExercise = it.sessionExercise.copy(muscleSnapshot = "Back"))
        })
        assertFalse(WorkoutShareRenderer.renderShareCardBitmap(source, WeightUnit.KG).sameAs(
            WorkoutShareRenderer.renderShareCardBitmap(differentGroup, WeightUnit.KG)))
        val empty = source.copy(rawExercises = source.exercises.map { it.copy(rawSets = emptyList()) })
        val emptyBack = differentGroup.copy(rawExercises = differentGroup.exercises.map { it.copy(rawSets = emptyList()) })
        assertTrue(WorkoutShareRenderer.renderShareCardBitmap(empty, WeightUnit.KG).sameAs(
            WorkoutShareRenderer.renderShareCardBitmap(emptyBack, WeightUnit.KG)))
    }

    @Test
    fun `wide numeric labels stay inside summary gutters in both themes`() {
        val source = detail(1).let { workout ->
            workout.copy(rawExercises = workout.exercises.map { exercise ->
                exercise.copy(rawSets = exercise.sets.map { it.copy(weightGrams = Long.MAX_VALUE / 16, reps = 1) })
            })
        }
        assertTrue(WorkoutShareContent.from(source, WeightUnit.KG, WorkoutShareOptions()).volume.length >= 20)
        for (dark in listOf(true, false)) {
            val card = WorkoutShareRenderer.renderShareCardBitmap(source, WeightUnit.KG, WorkoutShareOptions(darkCardStyle = dark))
            val background = if (dark) 0xFF0D1015.toInt() else 0xFFF7F7F4.toInt()
            for (y in 400..510) {
                assertEquals(background, card.getPixel(40, y))
                assertEquals(background, card.getPixel(1160, y))
            }
            File("build/reports/share-previews/large-number-${if (dark) "dark" else "light"}.png").apply {
                parentFile!!.mkdirs()
                outputStream().use { WorkoutShareRenderer.writeBitmapToOutputStream(card, it) }
            }
        }
    }

    @Test
    fun `numeric fitting retains the complete unit without layout ellipsis`() {
        for (value in listOf("19,440 kg", "9,919,810 lb", "2,305,843,009,213,694 kg")) {
            val width = if (value.length > 11) 550f else 174f
            val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                textSize = WorkoutShareRenderer.fittedValueSize(value, width)
            }
            val layout = android.text.StaticLayout.Builder.obtain(value, 0, value.length, paint, width.toInt())
                .setMaxLines(1).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
            assertEquals("Full numeric label: $value", 0, layout.getEllipsisCount(0))
        }
    }

    @Test fun `licensed anatomy responds to trained groups while equipment remains private`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        for (template in ShareTemplate.entries) {
            val options = WorkoutShareOptions(template, true)
            val card = WorkoutShareRenderer.renderShareCardBitmap(detail(), WeightUnit.KG, options, app)
            val metadataChanged = detail().copy(rawExercises = detail().exercises.map { it.copy(sessionExercise = it.sessionExercise.copy(muscleSnapshot = "Unknown", equipmentSnapshot = "Private equipment")) })
            assertFalse("Anatomy reflects recorded primary groups", card.sameAs(WorkoutShareRenderer.renderShareCardBitmap(metadataChanged, WeightUnit.KG, options, app)))
            val equipmentChanged=detail().copy(rawExercises=detail().exercises.map { it.copy(sessionExercise=it.sessionExercise.copy(equipmentSnapshot="PRIVATE EQUIPMENT")) })
            assertTrue("Equipment text stays private",card.sameAs(WorkoutShareRenderer.renderShareCardBitmap(equipmentChanged,WeightUnit.KG,options,app)))
            val encoded = ByteArrayOutputStream()
            WorkoutShareRenderer.writeBitmapToOutputStream(card, encoded)
            assertTrue(card.sameAs(BitmapFactory.decodeByteArray(encoded.toByteArray(), 0, encoded.size())))
            var black = 0
            for (y in 0 until card.height step 4) for (x in 0 until card.width step 4) {
                val color = card.getPixel(x, y)
                if (android.graphics.Color.alpha(color) > 0 && android.graphics.Color.red(color) < 32 && android.graphics.Color.green(color) < 32 && android.graphics.Color.blue(color) < 32) black++
            }
            assertTrue("Dark card must read near-black", black > (card.width / 4) * (card.height / 4) * 0.6)
            File("build/reports/share-previews/editorial-${template.name.lowercase()}-dark.png").apply { parentFile!!.mkdirs() }.outputStream().use { WorkoutShareRenderer.writeBitmapToOutputStream(card, it) }
        }
    }
}
