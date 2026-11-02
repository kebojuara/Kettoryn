package com.williamchandra.kettoryn.data

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.williamchandra.kettoryn.data.local.KettorynDatabase
import com.williamchandra.kettoryn.data.media.CustomExerciseMediaStore
import com.williamchandra.kettoryn.data.preferences.UserPreferences
import com.williamchandra.kettoryn.data.repository.WorkoutRepository
import com.williamchandra.kettoryn.data.backup.BackupManager
import com.williamchandra.kettoryn.data.backup.BackupValidationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CustomMediaBackupTest {
    private fun image(): ByteArray {
        val bitmap = Bitmap.createBitmap(1200,800, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(400f,200f,800f,600f,Paint().apply { color = Color.RED })
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG,100,it);bitmap.recycle() }.toByteArray()
    }
    @Test fun squareCropKeepsAlphaIsBoundedAndSurvivesAReopenedStore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = CustomExerciseMediaStore(context)
        val key = store.storeEncodedSource(image())
        assertTrue(CustomExerciseMediaStore.isValidKey(key))
        val encoded = CustomExerciseMediaStore(context).file(key)!!.readBytes()
        assertEquals(key, CustomExerciseMediaStore.sha256(encoded)+".webp")
        val bitmap = BitmapFactory.decodeByteArray(encoded,0,encoded.size)
        assertEquals(768,bitmap.width);assertEquals(768,bitmap.height)
        assertEquals(0,Color.alpha(bitmap.getPixel(0,0)))
        assertEquals(Color.RED,bitmap.getPixel(384,384));bitmap.recycle()
        try { store.storeEncodedSource(byteArrayOf(1,2,3));fail("Invalid image must fail") } catch (_: IllegalArgumentException) {}
        try { store.storeEncodedSource(ByteArray(CustomExerciseMediaStore.MAX_SOURCE_BYTES+1));fail("Oversize source must fail") } catch (_: IllegalArgumentException) {}
        assertTrue(store.file(key)!!.isFile)
        assertNull(store.file("../../private.webp"))
    }
    @Test fun photoBackupRoundtripHasIntegrityAndMissingOptionalMediaIsGraceful() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = CustomExerciseMediaStore(context);val key = store.storeEncodedSource(image())
        val db = Room.inMemoryDatabaseBuilder(context,KettorynDatabase::class.java).allowMainThreadQueries().build()
        val repo = WorkoutRepository(db,store.directory)
        try {
            repo.createCustomExercise("Photo movement","Chest","Dumbbell","","duration",key)
            val snapshot = repo.exportDatabaseSnapshot();val media = repo.readCustomMedia(snapshot)
            val output = ByteArrayOutputStream();BackupManager.writeBackupArchive(output,snapshot,UserPreferences(),customMedia=media)
            val staged = BackupManager.inspectAndStageBackup(output.toByteArray().inputStream())
            assertEquals(snapshot,staged.snapshot)
            assertArrayEquals(media.getValue(key),staged.customMedia.getValue(key))
            val archiveWithoutMedia = ByteArrayOutputStream();BackupManager.writeBackupArchive(archiveWithoutMedia,snapshot,UserPreferences())
            val missing = BackupManager.inspectAndStageBackup(archiveWithoutMedia.toByteArray().inputStream())
            assertEquals(key,missing.snapshot.exercises.single().customImageKey);assertTrue(missing.customMedia.isEmpty())
            val tampered = ByteArrayOutputStream()
            ZipOutputStream(tampered).use { target -> ZipInputStream(output.toByteArray().inputStream()).use { source ->
                while (true) { val entry=source.nextEntry?:break;val bytes=source.readBytes();target.putNextEntry(ZipEntry(entry.name));
                    if(entry.name.startsWith("media/")) bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();target.write(bytes);target.closeEntry() }
            } }
            try { BackupManager.inspectAndStageBackup(tampered.toByteArray().inputStream());fail("Changed photo hash must fail") } catch (_: BackupValidationException) {}
            val slip = ByteArrayOutputStream()
            ZipOutputStream(slip).use { it.putNextEntry(ZipEntry("media/../escape.webp"));it.write(byteArrayOf(1));it.closeEntry() }
            try { BackupManager.inspectAndStageBackup(slip.toByteArray().inputStream());fail("Path traversal must fail") } catch (_: BackupValidationException) {}
            assertEquals(snapshot,repo.exportDatabaseSnapshot())
        } finally { db.close() }
    }
}
