package com.williamchandra.kettoryn.data.backup

import com.williamchandra.kettoryn.data.media.CustomExerciseMediaStore
import com.williamchandra.kettoryn.data.local.ExerciseKinds
import com.williamchandra.kettoryn.data.local.CanonicalExerciseCatalog
import com.williamchandra.kettoryn.data.local.BodyMeasurementEntryEntity
import com.williamchandra.kettoryn.data.local.BodyMeasurementValueEntity
import com.williamchandra.kettoryn.data.local.ExerciseEntity
import com.williamchandra.kettoryn.data.local.LoggedSetEntity
import com.williamchandra.kettoryn.data.local.SessionExerciseEntity
import com.williamchandra.kettoryn.data.local.SessionStatus
import com.williamchandra.kettoryn.data.local.SetType
import com.williamchandra.kettoryn.data.local.TemplateExerciseEntity
import com.williamchandra.kettoryn.data.local.TemplateSetEntity
import com.williamchandra.kettoryn.data.local.WorkoutSessionEntity
import com.williamchandra.kettoryn.data.local.WorkoutTemplateEntity
import com.williamchandra.kettoryn.data.preferences.LengthUnit
import com.williamchandra.kettoryn.data.preferences.ThemeMode
import com.williamchandra.kettoryn.data.preferences.UserPreferences
import com.williamchandra.kettoryn.data.preferences.UserPreferencesRepository
import com.williamchandra.kettoryn.data.preferences.WeightUnit
import com.williamchandra.kettoryn.data.repository.DatabaseSnapshot
import com.williamchandra.kettoryn.data.repository.WorkoutRepository
import com.williamchandra.kettoryn.domain.MeasurementMetric
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupValidationException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

data class BackupPreview(
    val appId: String,
    val formatVersion: Int,
    val schemaVersion: Int,
    val appVersionName: String,
    val createdAtEpochMillis: Long,
    val createdAtUtcIso: String,
    val totalExercisesCount: Int,
    val customExercisesCount: Int,
    val templatesCount: Int,
    val completedWorkoutsCount: Int,
    val activeWorkoutsCount: Int,
    val loggedSetsCount: Int,
    val measurementEntriesCount: Int,
    val measurementValuesCount: Int,
    val weightUnit: WeightUnit,
    val lengthUnit: LengthUnit
)

data class RestoredPreferences(
    val weightUnit: WeightUnit,
    val lengthUnit: LengthUnit,
    val defaultRestSeconds: Int,
    val themeMode: ThemeMode
)

data class StagedBackup(
    val preview: BackupPreview,
    val snapshot: DatabaseSnapshot,
    val preferences: RestoredPreferences,
    val customMedia: Map<String, ByteArray> = emptyMap()
)

object BackupManager {
    const val APP_ID = "com.williamchandra.kettoryn"
    const val FORMAT_VERSION = 2
    const val CURRENT_SCHEMA_VERSION = 3
    const val APP_VERSION_NAME = "2.0.0"

    private const val ENTRY_MANIFEST = "manifest.json"
    private const val ENTRY_DATA = "data.json"
    private const val ENTRY_PREFERENCES = "preferences.json"
    private val ALLOWED_ENTRIES = setOf(ENTRY_MANIFEST, ENTRY_DATA, ENTRY_PREFERENCES)
    private const val MAX_ENTRY_BYTES = 16 * 1024 * 1024 // 16 MB per entry cap
    private const val MAX_TOTAL_BYTES = 32 * 1024 * 1024 // 32 MB total cap

    fun defaultBackupFileName(nowMillis: Long = System.currentTimeMillis()): String {
        val dateStr = Instant.ofEpochMilli(nowMillis)
            .atZone(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.US))
        return "kettoryn-backup-$dateStr.kettoryn"
    }

    suspend fun exportBackupToStream(
        outputStream: OutputStream,
        workoutRepository: WorkoutRepository,
        preferencesRepository: UserPreferencesRepository,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        val snapshot = workoutRepository.exportDatabaseSnapshot()
        val prefs = preferencesRepository.getSnapshot()
        writeBackupArchive(outputStream, snapshot, prefs, nowMillis, workoutRepository.readCustomMedia(snapshot))
    }

    fun writeBackupArchive(
        outputStream: OutputStream,
        snapshot: DatabaseSnapshot,
        prefs: UserPreferences,
        nowMillis: Long = System.currentTimeMillis(),
        customMedia: Map<String, ByteArray> = emptyMap()
    ) {
        val referenced = snapshot.exercises.mapNotNull { it.customImageKey }.toSet()
        require(customMedia.keys.all { it in referenced }) { "Backup contains unreferenced images." }
        require(customMedia.values.sumOf { it.size.toLong() } <= 24L * 1024 * 1024) { "Custom images exceed the 24 MB backup image budget." }
        customMedia.forEach { (key, bytes) -> validateMedia(key, bytes) }
        val dataBytes = serializeDataJson(snapshot).toByteArray(StandardCharsets.UTF_8)
        val prefsBytes = serializePreferencesJson(prefs).toByteArray(StandardCharsets.UTF_8)
        val dataSha256 = sha256Hex(dataBytes)
        val prefsSha256 = sha256Hex(prefsBytes)
        val manifestText = serializeManifestJson(
            snapshot = snapshot,
            dataSha256 = dataSha256,
            prefsSha256 = prefsSha256,
            nowMillis = nowMillis
        )
        val manifestBytes = JSONObject(manifestText).apply {
            put("customMedia", JSONArray().apply { customMedia.toSortedMap().forEach { (key, bytes) ->
                put(JSONObject().apply { put("key", key); put("sha256", sha256Hex(bytes)); put("bytes", bytes.size) })
            } })
        }.toString(2).toByteArray(StandardCharsets.UTF_8)

        ZipOutputStream(outputStream).use { zos ->
            writeZipEntry(zos, ENTRY_MANIFEST, manifestBytes)
            writeZipEntry(zos, ENTRY_DATA, dataBytes)
            writeZipEntry(zos, ENTRY_PREFERENCES, prefsBytes)
            customMedia.toSortedMap().forEach { (key, bytes) -> writeZipEntry(zos, "media/$key", bytes) }
            zos.finish()
        }
    }

    /**
     * Reads, validates, and stages a `.kettoryn` backup archive in memory without modifying
     * any live Room tables or DataStore preferences.
     * Throws [BackupValidationException] on any corruption, checksum mismatch, or schema violation.
     */
    fun inspectAndStageBackup(inputStream: InputStream): StagedBackup {
        val entries = mutableMapOf<String, ByteArray>()
        var totalBytesRead = 0

        try {
            ZipInputStream(inputStream).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val name = entry.name
                    val isMedia = Regex("media/[a-f0-9]{64}\\.webp").matches(name)
                    if (entry.isDirectory || name.contains("..") || name.contains("\\") || (name !in ALLOWED_ENTRIES && !isMedia)) {
                        throw BackupValidationException("Invalid or unexpected archive entry path: $name")
                    }
                    if (entries.containsKey(name)) {
                        throw BackupValidationException("Duplicate file in backup archive: $name")
                    }

                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    var entryBytes = 0
                    while (true) {
                        val read = zis.read(chunk)
                        if (read <= 0) break
                        entryBytes += read
                        totalBytesRead += read
                        if (entryBytes > MAX_ENTRY_BYTES || totalBytesRead > MAX_TOTAL_BYTES) {
                            throw BackupValidationException("Backup file exceeds maximum supported size.")
                        }
                        buffer.write(chunk, 0, read)
                    }
                    entries[name] = buffer.toByteArray()
                    zis.closeEntry()
                }
            }
        } catch (e: BackupValidationException) {
            throw e
        } catch (e: Exception) {
            throw BackupValidationException("Backup archive is unreadable or corrupted.", e)
        }

        val manifestBytes = entries[ENTRY_MANIFEST]
            ?: throw BackupValidationException("Backup archive is missing manifest.json.")
        val dataBytes = entries[ENTRY_DATA]
            ?: throw BackupValidationException("Backup archive is missing data.json.")
        val prefsBytes = entries[ENTRY_PREFERENCES]
            ?: throw BackupValidationException("Backup archive is missing preferences.json.")

        val manifestJson = try {
            JSONObject(String(manifestBytes, StandardCharsets.UTF_8))
        } catch (e: Exception) {
            throw BackupValidationException("Backup manifest is malformed JSON.", e)
        }

        val appId = manifestJson.optString("appId", "")
        if (appId != APP_ID) {
            throw BackupValidationException("Backup belongs to an unsupported application ($appId).")
        }
        val formatVersion = manifestJson.optInt("formatVersion", -1)
        if (formatVersion !in 1..FORMAT_VERSION) {
            throw BackupValidationException("Unsupported backup format version ($formatVersion).")
        }
        val schemaVersion = manifestJson.optInt("schemaVersion", -1)
        if (schemaVersion !in 1..CURRENT_SCHEMA_VERSION) {
            throw BackupValidationException("Unsupported database schema version ($schemaVersion). Update Kettoryn to restore this backup.")
        }

        val checksumsObj = manifestJson.optJSONObject("checksums")
            ?: throw BackupValidationException("Backup manifest is missing checksums.")
        val expectedDataSha = checksumsObj.optString("dataSha256", "")
        val expectedPrefsSha = checksumsObj.optString("preferencesSha256", "")

        val actualDataSha = sha256Hex(dataBytes)
        val actualPrefsSha = sha256Hex(prefsBytes)
        if (!actualDataSha.equals(expectedDataSha, ignoreCase = true)) {
            throw BackupValidationException("Backup data checksum mismatch. The file may be corrupted or modified.")
        }
        if (!actualPrefsSha.equals(expectedPrefsSha, ignoreCase = true)) {
            throw BackupValidationException("Backup preferences checksum mismatch. The file may be corrupted or modified.")
        }

        val restoredPrefs = try {
            parsePreferencesJson(JSONObject(String(prefsBytes, StandardCharsets.UTF_8)))
        } catch (e: BackupValidationException) {
            throw e
        } catch (e: Exception) {
            throw BackupValidationException("Backup preferences JSON is invalid.", e)
        }

        val snapshot = try {
            parseDataJson(JSONObject(String(dataBytes, StandardCharsets.UTF_8)))
        } catch (e: BackupValidationException) {
            throw e
        } catch (e: Exception) {
            throw BackupValidationException("Backup workout data JSON is invalid.", e)
        }

        validateSnapshotIntegrity(snapshot, manifestJson.optJSONObject("counts"))
        val media = linkedMapOf<String, ByteArray>()
        val listed = manifestJson.optJSONArray("customMedia") ?: JSONArray()
        val references = snapshot.exercises.mapNotNull { it.customImageKey }.toSet()
        for (i in 0 until listed.length()) {
            val row = listed.getJSONObject(i); val key = row.getString("key")
            if (!CustomExerciseMediaStore.isValidKey(key) || key !in references || key in media) throw BackupValidationException("Invalid image inventory.")
            val bytes = entries["media/$key"] ?: throw BackupValidationException("Backup is missing a listed image.")
            if (row.getLong("bytes") != bytes.size.toLong() || row.getString("sha256") != sha256Hex(bytes)) throw BackupValidationException("Backup image checksum mismatch.")
            validateMedia(key, bytes); media[key] = bytes
        }
        if (entries.keys.count { it.startsWith("media/") } != media.size || media.values.sumOf { it.size.toLong() } > 24L * 1024 * 1024) throw BackupValidationException("Unexpected or oversized backup images.")


        val createdAtMillis = manifestJson.optLong("createdAtEpochMillis", 0L).takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val createdAtIso = manifestJson.optString("createdAtUtc", "")
        val appVersionName = manifestJson.optString("appVersionName", "2.0")

        val preview = BackupPreview(
            appId = appId,
            formatVersion = formatVersion,
            schemaVersion = schemaVersion,
            appVersionName = appVersionName,
            createdAtEpochMillis = createdAtMillis,
            createdAtUtcIso = createdAtIso,
            totalExercisesCount = snapshot.exercises.size,
            customExercisesCount = snapshot.exercises.count { !it.isBuiltIn && it.deletedAt == null },
            templatesCount = snapshot.templates.size,
            completedWorkoutsCount = snapshot.sessions.count { it.status == SessionStatus.COMPLETED },
            activeWorkoutsCount = snapshot.sessions.count { it.status == SessionStatus.ACTIVE },
            loggedSetsCount = snapshot.loggedSets.size,
            measurementEntriesCount = snapshot.measurementEntries.size,
            measurementValuesCount = snapshot.measurementValues.size,
            weightUnit = restoredPrefs.weightUnit,
            lengthUnit = restoredPrefs.lengthUnit
        )

        return StagedBackup(
            preview = preview,
            snapshot = snapshot,
            preferences = restoredPrefs,
            customMedia = media
        )
    }

    suspend fun applyStagedBackup(
        staged: StagedBackup,
        workoutRepository: WorkoutRepository,
        preferencesRepository: UserPreferencesRepository
    ) {
        workoutRepository.restoreCustomMedia(staged.customMedia)
        workoutRepository.replaceAllDatabaseData(staged.snapshot)
        preferencesRepository.replacePreferences(
            weightUnit = staged.preferences.weightUnit,
            lengthUnit = staged.preferences.lengthUnit,
            defaultRestSeconds = staged.preferences.defaultRestSeconds,
            themeMode = staged.preferences.themeMode
        )
    }

    private fun validateMedia(key: String, bytes: ByteArray) {
        if (!CustomExerciseMediaStore.isValidKey(key) || bytes.size > CustomExerciseMediaStore.MAX_MEDIA_BYTES || sha256Hex(bytes) + ".webp" != key) throw BackupValidationException("Invalid image hash or size.")
        if (bytes.size < 12 || String(bytes, 0, 4, StandardCharsets.US_ASCII) != "RIFF" || String(bytes, 8, 4, StandardCharsets.US_ASCII) != "WEBP") throw BackupValidationException("Invalid image encoding.")
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..768 || bounds.outWidth != bounds.outHeight) throw BackupValidationException("Invalid square image dimensions.")
    }

    private fun validateSnapshotIntegrity(snapshot: DatabaseSnapshot, countsObj: JSONObject?) {
        val slugs = hashSetOf<String>()
        snapshot.exercises.forEach { exercise ->
            if (exercise.exerciseType !in ExerciseKinds.labels) throw BackupValidationException("Unsupported exercise type.")
            if (exercise.customImageKey != null && (!CustomExerciseMediaStore.isValidKey(exercise.customImageKey) || exercise.isBuiltIn)) throw BackupValidationException("Invalid custom image reference.")
            exercise.sourceSlug?.let { slug ->
                val official = CanonicalExerciseCatalog.bySlug[slug] ?: throw BackupValidationException("Unsupported catalog identity.")
                if (!exercise.isBuiltIn || !slugs.add(slug) || exercise.name != official.name || exercise.primaryMuscle != official.primaryMuscle || exercise.equipment != official.equipment || exercise.exerciseType != official.exerciseType) throw BackupValidationException("Invalid or duplicate catalog identity.")
            }
        }
        snapshot.loggedSets.forEach { set ->
            if (set.exerciseType !in ExerciseKinds.labels || set.durationSeconds?.let { it !in 0..604800 } == true || set.distanceMeters?.let { it !in 0..10000000 } == true) throw BackupValidationException("Invalid duration or distance.")
        }
        fun <T> requireUniquePositiveIds(items: List<T>, label: String, idSelector: (T) -> Long): Set<Long> {
            val seen = HashSet<Long>(items.size)
            for (item in items) {
                val id = idSelector(item)
                if (id <= 0L) {
                    throw BackupValidationException("Invalid primary key ID ($id) in $label.")
                }
                if (!seen.add(id)) {
                    throw BackupValidationException("Duplicate primary key ID ($id) in $label.")
                }
            }
            return seen
        }

        val exerciseIds = requireUniquePositiveIds(snapshot.exercises, "exercises") { it.id }
        val templateIds = requireUniquePositiveIds(snapshot.templates, "templates") { it.id }
        val templateExerciseIds = requireUniquePositiveIds(snapshot.templateExercises, "templateExercises") { it.id }
        requireUniquePositiveIds(snapshot.templateSets, "templateSets") { it.id }
        val sessionIds = requireUniquePositiveIds(snapshot.sessions, "sessions") { it.id }
        val sessionExerciseIds = requireUniquePositiveIds(snapshot.sessionExercises, "sessionExercises") { it.id }
        requireUniquePositiveIds(snapshot.loggedSets, "loggedSets") { it.id }
        val measurementEntryIds = requireUniquePositiveIds(snapshot.measurementEntries, "measurementEntries") { it.id }
        requireUniquePositiveIds(snapshot.measurementValues, "measurementValues") { it.id }

        for (ex in snapshot.exercises) {
            if (ex.name.isBlank()) throw BackupValidationException("Exercise ${ex.id} has a blank name.")
        }
        for (tpl in snapshot.templates) {
            if (tpl.title.isBlank()) throw BackupValidationException("Template ${tpl.id} has a blank title.")
        }
        for (te in snapshot.templateExercises) {
            if (te.templateId !in templateIds) {
                throw BackupValidationException("Template exercise ${te.id} references missing template ${te.templateId}.")
            }
            if (te.exerciseId != null && te.exerciseId !in exerciseIds) {
                throw BackupValidationException("Template exercise ${te.id} references missing exercise ${te.exerciseId}.")
            }
        }
        for (ts in snapshot.templateSets) {
            if (ts.templateExerciseId !in templateExerciseIds) {
                throw BackupValidationException("Template set ${ts.id} references missing templateExercise ${ts.templateExerciseId}.")
            }
        }
        val activeSessions = snapshot.sessions.count { it.status == SessionStatus.ACTIVE }
        if (activeSessions > 1) {
            throw BackupValidationException("Backup contains multiple active workout sessions ($activeSessions).")
        }
        for (ws in snapshot.sessions) {
            if (ws.sourceTemplateId != null && ws.sourceTemplateId !in templateIds) {
                throw BackupValidationException("Workout session ${ws.id} references missing template ${ws.sourceTemplateId}.")
            }
        }
        for (se in snapshot.sessionExercises) {
            if (se.sessionId !in sessionIds) {
                throw BackupValidationException("Session exercise ${se.id} references missing session ${se.sessionId}.")
            }
            if (se.exerciseId != null && se.exerciseId !in exerciseIds) {
                throw BackupValidationException("Session exercise ${se.id} references missing exercise ${se.exerciseId}.")
            }
        }
        for (ls in snapshot.loggedSets) {
            if (ls.sessionExerciseId !in sessionExerciseIds) {
                throw BackupValidationException("Logged set ${ls.id} references missing sessionExercise ${ls.sessionExerciseId}.")
            }
            if ((ls.weightGrams ?: 0L) < 0L || (ls.reps ?: 0) < 0) {
                throw BackupValidationException("Logged set ${ls.id} has negative weight or reps.")
            }
        }
        val seenEntryMetricPairs = HashSet<String>()
        for (mv in snapshot.measurementValues) {
            if (mv.entryId !in measurementEntryIds) {
                throw BackupValidationException("Measurement value ${mv.id} references missing entry ${mv.entryId}.")
            }
            if (MeasurementMetric.fromKey(mv.metricKey) == null) {
                throw BackupValidationException("Unknown measurement metric key: ${mv.metricKey}")
            }
            if (mv.canonicalValue <= 0L) {
                throw BackupValidationException("Measurement value ${mv.id} has non-positive value ${mv.canonicalValue}.")
            }
            if (!seenEntryMetricPairs.add("${mv.entryId}:${mv.metricKey}")) {
                throw BackupValidationException("Duplicate metric ${mv.metricKey} in measurement entry ${mv.entryId}.")
            }
        }

        if (countsObj != null) {
            val expectedExercises = countsObj.optInt("exercises", snapshot.exercises.size)
            val expectedTemplates = countsObj.optInt("templates", snapshot.templates.size)
            val expectedSessions = countsObj.optInt("sessions", snapshot.sessions.size)
            val expectedMeasurementEntries = countsObj.optInt("measurementEntries", snapshot.measurementEntries.size)
            if (expectedExercises != snapshot.exercises.size ||
                expectedTemplates != snapshot.templates.size ||
                expectedSessions != snapshot.sessions.size ||
                expectedMeasurementEntries != snapshot.measurementEntries.size
            ) {
                throw BackupValidationException("Backup manifest entity counts do not match data payload.")
            }
        }
    }

    private fun serializeManifestJson(
        snapshot: DatabaseSnapshot,
        dataSha256: String,
        prefsSha256: String,
        nowMillis: Long
    ): String {
        val isoUtc = Instant.ofEpochMilli(nowMillis)
            .atZone(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_INSTANT)
        val counts = JSONObject().apply {
            put("exercises", snapshot.exercises.size)
            put("customExercises", snapshot.exercises.count { !it.isBuiltIn && it.deletedAt == null })
            put("templates", snapshot.templates.size)
            put("templateExercises", snapshot.templateExercises.size)
            put("templateSets", snapshot.templateSets.size)
            put("sessions", snapshot.sessions.size)
            put("completedWorkouts", snapshot.sessions.count { it.status == SessionStatus.COMPLETED })
            put("activeWorkouts", snapshot.sessions.count { it.status == SessionStatus.ACTIVE })
            put("sessionExercises", snapshot.sessionExercises.size)
            put("loggedSets", snapshot.loggedSets.size)
            put("measurementEntries", snapshot.measurementEntries.size)
            put("measurementValues", snapshot.measurementValues.size)
        }
        val checksums = JSONObject().apply {
            put("dataSha256", dataSha256)
            put("preferencesSha256", prefsSha256)
        }
        return JSONObject().apply {
            put("appId", APP_ID)
            put("formatVersion", FORMAT_VERSION)
            put("schemaVersion", CURRENT_SCHEMA_VERSION)
            put("appVersionName", APP_VERSION_NAME)
            put("createdAtEpochMillis", nowMillis)
            put("createdAtUtc", isoUtc)
            put("counts", counts)
            put("checksums", checksums)
        }.toString(2)
    }

    private fun serializePreferencesJson(prefs: UserPreferences): String {
        return JSONObject().apply {
            put("weightUnit", prefs.weightUnit.name)
            put("lengthUnit", prefs.lengthUnit.name)
            put("defaultRestSeconds", prefs.defaultRestSeconds)
            put("themeMode", prefs.themeMode.name)
        }.toString(2)
    }

    private fun parsePreferencesJson(json: JSONObject): RestoredPreferences {
        val weightUnitStr = json.optString("weightUnit", WeightUnit.KG.name)
        val weightUnit = runCatching { WeightUnit.valueOf(weightUnitStr) }.getOrElse {
            throw BackupValidationException("Invalid weight unit in backup preferences: $weightUnitStr")
        }
        val lengthUnitStr = json.optString("lengthUnit", LengthUnit.CM.name)
        val lengthUnit = runCatching { LengthUnit.valueOf(lengthUnitStr) }.getOrElse {
            throw BackupValidationException("Invalid length unit in backup preferences: $lengthUnitStr")
        }
        val restSeconds = json.optInt("defaultRestSeconds", 90)
        if (restSeconds !in 0..3600) {
            throw BackupValidationException("Invalid defaultRestSeconds in backup preferences: $restSeconds")
        }
        val themeModeStr = json.optString("themeMode", ThemeMode.DARK.name)
        val themeMode = runCatching { ThemeMode.valueOf(themeModeStr) }.getOrElse {
            throw BackupValidationException("Invalid theme mode in backup preferences: $themeModeStr")
        }
        return RestoredPreferences(
            weightUnit = weightUnit,
            lengthUnit = lengthUnit,
            defaultRestSeconds = restSeconds,
            themeMode = themeMode
        )
    }

    private fun serializeDataJson(snapshot: DatabaseSnapshot): String {
        val root = JSONObject()

        root.put("exercises", JSONArray().apply {
            for (e in snapshot.exercises) {
                put(JSONObject().apply {
                    put("id", e.id)
                    put("name", e.name)
                    put("normalizedName", e.normalizedName)
                    put("primaryMuscle", e.primaryMuscle)
                    put("equipment", e.equipment)
                    put("instructions", e.instructions)
                    put("isBuiltIn", e.isBuiltIn)
                    put("sourceSlug", e.sourceSlug ?: JSONObject.NULL)
                    put("exerciseType", e.exerciseType)
                    put("secondaryMuscles", e.secondaryMuscles)
                    put("isStretch", e.isStretch)
                    put("customImageKey", e.customImageKey ?: JSONObject.NULL)
                    if (e.deletedAt != null) put("deletedAt", e.deletedAt) else put("deletedAt", JSONObject.NULL)
                })
            }
        })

        root.put("templates", JSONArray().apply {
            for (t in snapshot.templates) {
                put(JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("createdAt", t.createdAt)
                    put("updatedAt", t.updatedAt)
                })
            }
        })

        root.put("templateExercises", JSONArray().apply {
            for (te in snapshot.templateExercises) {
                put(JSONObject().apply {
                    put("id", te.id)
                    put("templateId", te.templateId)
                    if (te.exerciseId != null) put("exerciseId", te.exerciseId) else put("exerciseId", JSONObject.NULL)
                    put("sortOrder", te.sortOrder)
                    put("exerciseNameSnapshot", te.exerciseNameSnapshot)
                    put("muscleSnapshot", te.muscleSnapshot)
                    put("equipmentSnapshot", te.equipmentSnapshot)
                    put("sourceSlugSnapshot", te.sourceSlugSnapshot ?: JSONObject.NULL)
                    put("exerciseTypeSnapshot", te.exerciseTypeSnapshot)
                })
            }
        })

        root.put("templateSets", JSONArray().apply {
            for (ts in snapshot.templateSets) {
                put(JSONObject().apply {
                    put("id", ts.id)
                    put("templateExerciseId", ts.templateExerciseId)
                    put("sortOrder", ts.sortOrder)
                    put("suggestedDurationSeconds", ts.suggestedDurationSeconds ?: JSONObject.NULL)
                    put("suggestedDistanceMeters", ts.suggestedDistanceMeters ?: JSONObject.NULL)
                    if (ts.suggestedReps != null) put("suggestedReps", ts.suggestedReps) else put("suggestedReps", JSONObject.NULL)
                    if (ts.suggestedWeightGrams != null) put("suggestedWeightGrams", ts.suggestedWeightGrams) else put("suggestedWeightGrams", JSONObject.NULL)
                })
            }
        })

        root.put("sessions", JSONArray().apply {
            for (s in snapshot.sessions) {
                put(JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    if (s.sourceTemplateId != null) put("sourceTemplateId", s.sourceTemplateId) else put("sourceTemplateId", JSONObject.NULL)
                    put("startedAt", s.startedAt)
                    if (s.endedAt != null) put("endedAt", s.endedAt) else put("endedAt", JSONObject.NULL)
                    put("status", s.status.name)
                    put("updatedAt", s.updatedAt)
                })
            }
        })

        root.put("sessionExercises", JSONArray().apply {
            for (se in snapshot.sessionExercises) {
                put(JSONObject().apply {
                    put("id", se.id)
                    put("sessionId", se.sessionId)
                    if (se.exerciseId != null) put("exerciseId", se.exerciseId) else put("exerciseId", JSONObject.NULL)
                    put("nameSnapshot", se.nameSnapshot)
                    put("muscleSnapshot", se.muscleSnapshot)
                    put("equipmentSnapshot", se.equipmentSnapshot)
                    put("sourceSlugSnapshot", se.sourceSlugSnapshot ?: JSONObject.NULL)
                    put("exerciseTypeSnapshot", se.exerciseTypeSnapshot)
                    put("sortOrder", se.sortOrder)
                })
            }
        })

        root.put("loggedSets", JSONArray().apply {
            for (ls in snapshot.loggedSets) {
                put(JSONObject().apply {
                    put("id", ls.id)
                    put("sessionExerciseId", ls.sessionExerciseId)
                    put("sortOrder", ls.sortOrder)
                    if (ls.reps != null) put("reps", ls.reps) else put("reps", JSONObject.NULL)
                    if (ls.weightGrams != null) put("weightGrams", ls.weightGrams) else put("weightGrams", JSONObject.NULL)
                    put("isComplete", ls.isComplete)
                    put("setType", ls.setType.name)
                    put("exerciseType", ls.exerciseType)
                    put("durationSeconds", ls.durationSeconds ?: JSONObject.NULL)
                    put("distanceMeters", ls.distanceMeters ?: JSONObject.NULL)
                })
            }
        })

        root.put("measurementEntries", JSONArray().apply {
            for (me in snapshot.measurementEntries) {
                put(JSONObject().apply {
                    put("id", me.id)
                    put("recordedAt", me.recordedAt)
                    put("updatedAt", me.updatedAt)
                    put("note", me.note)
                })
            }
        })

        root.put("measurementValues", JSONArray().apply {
            for (mv in snapshot.measurementValues) {
                put(JSONObject().apply {
                    put("id", mv.id)
                    put("entryId", mv.entryId)
                    put("metricKey", mv.metricKey)
                    put("canonicalValue", mv.canonicalValue)
                })
            }
        })

        return root.toString(2)
    }

    private fun parseDataJson(root: JSONObject): DatabaseSnapshot {
        fun JSONObject.optNullableLong(key: String): Long? =
            if (!has(key) || isNull(key)) null else getLong(key)

        fun JSONObject.optNullableInt(key: String): Int? =
            if (!has(key) || isNull(key)) null else getInt(key)

        val exercisesArr = root.getJSONArray("exercises")
        val exercises = List(exercisesArr.length()) { i ->
            val o = exercisesArr.getJSONObject(i)
            ExerciseEntity(
                id = o.getLong("id"),
                name = o.getString("name"),
                normalizedName = o.getString("normalizedName"),
                primaryMuscle = o.getString("primaryMuscle"),
                equipment = o.getString("equipment"),
                instructions = o.optString("instructions", ""),
                isBuiltIn = o.getBoolean("isBuiltIn"),
                deletedAt = o.optNullableLong("deletedAt"),
                sourceSlug = if (o.isNull("sourceSlug")) null else o.optString("sourceSlug").takeIf { it.isNotEmpty() },
                exerciseType = o.optString("exerciseType", "weight_reps"),
                secondaryMuscles = o.optString("secondaryMuscles", "[]"),
                isStretch = o.optBoolean("isStretch", false),
                customImageKey = if (o.isNull("customImageKey")) null else o.optString("customImageKey").takeIf { it.isNotEmpty() }
            )
        }

        val templatesArr = root.getJSONArray("templates")
        val templates = List(templatesArr.length()) { i ->
            val o = templatesArr.getJSONObject(i)
            WorkoutTemplateEntity(
                id = o.getLong("id"),
                title = o.getString("title"),
                createdAt = o.getLong("createdAt"),
                updatedAt = o.getLong("updatedAt")
            )
        }

        val templateExercisesArr = root.getJSONArray("templateExercises")
        val templateExercises = List(templateExercisesArr.length()) { i ->
            val o = templateExercisesArr.getJSONObject(i)
            TemplateExerciseEntity(
                id = o.getLong("id"),
                templateId = o.getLong("templateId"),
                exerciseId = o.optNullableLong("exerciseId"),
                sortOrder = o.getInt("sortOrder"),
                exerciseNameSnapshot = o.getString("exerciseNameSnapshot"),
                muscleSnapshot = o.getString("muscleSnapshot"),
                equipmentSnapshot = o.getString("equipmentSnapshot"),
                sourceSlugSnapshot = if (o.isNull("sourceSlugSnapshot")) null else o.optString("sourceSlugSnapshot").takeIf { it.isNotEmpty() },
                exerciseTypeSnapshot = o.optString("exerciseTypeSnapshot", "weight_reps")
            )
        }

        val templateSetsArr = root.getJSONArray("templateSets")
        val templateSets = List(templateSetsArr.length()) { i ->
            val o = templateSetsArr.getJSONObject(i)
            TemplateSetEntity(
                id = o.getLong("id"),
                templateExerciseId = o.getLong("templateExerciseId"),
                sortOrder = o.getInt("sortOrder"),
                suggestedReps = o.optNullableInt("suggestedReps"),
                suggestedWeightGrams = o.optNullableLong("suggestedWeightGrams"),
                suggestedDurationSeconds = o.optNullableInt("suggestedDurationSeconds"),
                suggestedDistanceMeters = o.optNullableLong("suggestedDistanceMeters")
            )
        }

        val sessionsArr = root.getJSONArray("sessions")
        val sessions = List(sessionsArr.length()) { i ->
            val o = sessionsArr.getJSONObject(i)
            val statusStr = o.getString("status")
            val status = runCatching { SessionStatus.valueOf(statusStr) }.getOrElse {
                throw BackupValidationException("Invalid session status: $statusStr")
            }
            WorkoutSessionEntity(
                id = o.getLong("id"),
                title = o.getString("title"),
                sourceTemplateId = o.optNullableLong("sourceTemplateId"),
                startedAt = o.getLong("startedAt"),
                endedAt = o.optNullableLong("endedAt"),
                status = status,
                updatedAt = o.getLong("updatedAt")
            )
        }

        val sessionExercisesArr = root.getJSONArray("sessionExercises")
        val sessionExercises = List(sessionExercisesArr.length()) { i ->
            val o = sessionExercisesArr.getJSONObject(i)
            SessionExerciseEntity(
                id = o.getLong("id"),
                sessionId = o.getLong("sessionId"),
                exerciseId = o.optNullableLong("exerciseId"),
                nameSnapshot = o.getString("nameSnapshot"),
                muscleSnapshot = o.getString("muscleSnapshot"),
                equipmentSnapshot = o.getString("equipmentSnapshot"),
                sortOrder = o.getInt("sortOrder"),
                sourceSlugSnapshot = if (o.isNull("sourceSlugSnapshot")) null else o.optString("sourceSlugSnapshot").takeIf { it.isNotEmpty() },
                exerciseTypeSnapshot = o.optString("exerciseTypeSnapshot", "weight_reps")
            )
        }

        val loggedSetsArr = root.getJSONArray("loggedSets")
        val loggedSets = List(loggedSetsArr.length()) { i ->
            val o = loggedSetsArr.getJSONObject(i)
            val setTypeStr = o.getString("setType")
            val setType = runCatching { SetType.valueOf(setTypeStr) }.getOrElse {
                throw BackupValidationException("Invalid setType: $setTypeStr")
            }
            LoggedSetEntity(
                id = o.getLong("id"),
                sessionExerciseId = o.getLong("sessionExerciseId"),
                sortOrder = o.getInt("sortOrder"),
                reps = o.optNullableInt("reps"),
                weightGrams = o.optNullableLong("weightGrams"),
                isComplete = o.getBoolean("isComplete"),
                setType = setType,
                exerciseType = o.optString("exerciseType", "weight_reps"),
                durationSeconds = o.optNullableInt("durationSeconds"),
                distanceMeters = o.optNullableLong("distanceMeters")
            )
        }

        val measurementEntriesArr = root.optJSONArray("measurementEntries") ?: JSONArray()
        val measurementEntries = List(measurementEntriesArr.length()) { i ->
            val o = measurementEntriesArr.getJSONObject(i)
            BodyMeasurementEntryEntity(
                id = o.getLong("id"),
                recordedAt = o.getLong("recordedAt"),
                updatedAt = o.getLong("updatedAt"),
                note = o.optString("note", "")
            )
        }

        val measurementValuesArr = root.optJSONArray("measurementValues") ?: JSONArray()
        val measurementValues = List(measurementValuesArr.length()) { i ->
            val o = measurementValuesArr.getJSONObject(i)
            BodyMeasurementValueEntity(
                id = o.getLong("id"),
                entryId = o.getLong("entryId"),
                metricKey = o.getString("metricKey"),
                canonicalValue = o.getLong("canonicalValue")
            )
        }

        return DatabaseSnapshot(
            exercises = exercises,
            templates = templates,
            templateExercises = templateExercises,
            templateSets = templateSets,
            sessions = sessions,
            sessionExercises = sessionExercises,
            loggedSets = loggedSets,
            measurementEntries = measurementEntries,
            measurementValues = measurementValues
        )
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name)
        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format(Locale.US, "%02x", b))
        }
        return sb.toString()
    }
}

