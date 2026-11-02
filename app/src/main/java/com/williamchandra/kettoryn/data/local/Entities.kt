package com.williamchandra.kettoryn.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.williamchandra.kettoryn.domain.MeasurementMetric

enum class SessionStatus {
    ACTIVE,
    COMPLETED
}

enum class SetType {
    WARMUP,
    WORK
}

class KettorynConverters {
    @TypeConverter
    fun fromSessionStatus(status: SessionStatus): String = status.name

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus =
        runCatching { SessionStatus.valueOf(value) }.getOrDefault(SessionStatus.ACTIVE)

    @TypeConverter
    fun fromSetType(type: SetType): String = type.name

    @TypeConverter
    fun toSetType(value: String): SetType =
        runCatching { SetType.valueOf(value) }.getOrDefault(SetType.WORK)
}

@Entity(
    tableName = "exercises",
    indices = [
        Index(value = ["normalizedName"]),
        Index(value = ["primaryMuscle"]),
        Index(value = ["deletedAt"]),
        Index(value = ["sourceSlug"], unique = true)
    ]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val normalizedName: String,
    val primaryMuscle: String,
    val equipment: String,
    val instructions: String = "",
    val isBuiltIn: Boolean = false,
    val deletedAt: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val sourceSlug: String? = null,
    @ColumnInfo(defaultValue = "'weight_reps'") val exerciseType: String = "weight_reps",
    @ColumnInfo(defaultValue = "'[]'") val secondaryMuscles: String = "[]",
    @ColumnInfo(defaultValue = "0") val isStretch: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val customImageKey: String? = null
)

@Entity(
    tableName = "workout_templates",
    indices = [Index(value = ["updatedAt"])]
)
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "template_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["templateId", "sortOrder"]),
        Index(value = ["exerciseId"])
    ]
)
data class TemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val templateId: Long,
    val exerciseId: Long?,
    val sortOrder: Int,
    val exerciseNameSnapshot: String,
    val muscleSnapshot: String,
    val equipmentSnapshot: String,
    @ColumnInfo(defaultValue = "NULL") val sourceSlugSnapshot: String? = null,
    @ColumnInfo(defaultValue = "'weight_reps'") val exerciseTypeSnapshot: String = "weight_reps"
)

@Entity(
    tableName = "template_sets",
    foreignKeys = [
        ForeignKey(
            entity = TemplateExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateExerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["templateExerciseId", "sortOrder"])]
)
data class TemplateSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val templateExerciseId: Long,
    val sortOrder: Int,
    val suggestedReps: Int? = null,
    val suggestedWeightGrams: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val suggestedDurationSeconds: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val suggestedDistanceMeters: Long? = null
)

@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceTemplateId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["status", "startedAt"]),
        Index(value = ["sourceTemplateId"])
    ]
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val sourceTemplateId: Long? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val updatedAt: Long
)

@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["sessionId", "sortOrder"]),
        Index(value = ["exerciseId"])
    ]
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Long,
    val exerciseId: Long?,
    val nameSnapshot: String,
    val muscleSnapshot: String,
    val equipmentSnapshot: String,
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "NULL") val sourceSlugSnapshot: String? = null,
    @ColumnInfo(defaultValue = "'weight_reps'") val exerciseTypeSnapshot: String = "weight_reps"
)

@Entity(
    tableName = "logged_sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionExerciseId", "sortOrder"])]
)
data class LoggedSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionExerciseId: Long,
    val sortOrder: Int,
    val reps: Int? = null,
    val weightGrams: Long? = null,
    val isComplete: Boolean = false,
    val setType: SetType = SetType.WORK,
    @ColumnInfo(defaultValue = "'weight_reps'") val exerciseType: String = "weight_reps",
    @ColumnInfo(defaultValue = "NULL") val durationSeconds: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val distanceMeters: Long? = null
)

@Entity(
    tableName = "body_measurement_entries",
    indices = [Index(value = ["recordedAt"])]
)
data class BodyMeasurementEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val recordedAt: Long,
    val updatedAt: Long,
    val note: String = ""
)

@Entity(
    tableName = "body_measurement_values",
    foreignKeys = [
        ForeignKey(
            entity = BodyMeasurementEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["entryId", "metricKey"], unique = true),
        Index(value = ["metricKey"])
    ]
)
data class BodyMeasurementValueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val entryId: Long,
    val metricKey: String,
    val canonicalValue: Long
)

data class BodyMeasurementEntryWithValues(
    @Embedded val entry: BodyMeasurementEntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "entryId"
    )
    val rawValues: List<BodyMeasurementValueEntity>
) {
    val valuesMap: Map<MeasurementMetric, Long>
        get() = buildMap {
            for (v in rawValues) {
                val metric = MeasurementMetric.fromKey(v.metricKey)
                if (metric != null) {
                    put(metric, v.canonicalValue)
                }
            }
        }

    fun valueFor(metric: MeasurementMetric): Long? = valuesMap[metric]
}

data class TemplateExerciseWithSets(
    @Embedded val templateExercise: TemplateExerciseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "templateExerciseId"
    )
    val rawSets: List<TemplateSetEntity>
) {
    val sets: List<TemplateSetEntity>
        get() = rawSets.sortedBy { it.sortOrder }
}

data class TemplateWithExercises(
    @Embedded val template: WorkoutTemplateEntity,
    @Relation(
        entity = TemplateExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "templateId"
    )
    val rawExercises: List<TemplateExerciseWithSets>
) {
    val exercises: List<TemplateExerciseWithSets>
        get() = rawExercises.sortedBy { it.templateExercise.sortOrder }
}

data class SessionExerciseWithSets(
    @Embedded val sessionExercise: SessionExerciseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionExerciseId"
    )
    val rawSets: List<LoggedSetEntity>
) {
    val sets: List<LoggedSetEntity>
        get() = rawSets.sortedBy { it.sortOrder }
}

data class SessionWithExercises(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(
        entity = SessionExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val rawExercises: List<SessionExerciseWithSets>
) {
    val exercises: List<SessionExerciseWithSets>
        get() = rawExercises.sortedBy { it.sessionExercise.sortOrder }
}
