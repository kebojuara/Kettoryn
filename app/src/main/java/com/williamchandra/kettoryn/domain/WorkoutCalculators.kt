package com.williamchandra.kettoryn.domain

import com.williamchandra.kettoryn.data.local.LoggedSetEntity
import com.williamchandra.kettoryn.data.local.SetType
import com.williamchandra.kettoryn.data.preferences.LengthUnit
import com.williamchandra.kettoryn.data.preferences.WeightUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToLong

enum class MeasurementValueKind {
    WEIGHT,
    LENGTH,
    PERCENTAGE
}

enum class MeasurementMetric(
    val key: String,
    val label: String,
    val kind: MeasurementValueKind
) {
    BODY_WEIGHT("body_weight", "Body Weight", MeasurementValueKind.WEIGHT),
    WAIST("waist", "Waist", MeasurementValueKind.LENGTH),
    BODY_FAT("body_fat", "Body Fat", MeasurementValueKind.PERCENTAGE),
    LEAN_BODY_MASS("lean_body_mass", "Lean Body Mass", MeasurementValueKind.WEIGHT),
    NECK("neck", "Neck", MeasurementValueKind.LENGTH),
    SHOULDER("shoulder", "Shoulder", MeasurementValueKind.LENGTH),
    CHEST("chest", "Chest", MeasurementValueKind.LENGTH),
    LEFT_BICEP("left_bicep", "Left Bicep", MeasurementValueKind.LENGTH),
    RIGHT_BICEP("right_bicep", "Right Bicep", MeasurementValueKind.LENGTH),
    LEFT_FOREARM("left_forearm", "Left Forearm", MeasurementValueKind.LENGTH),
    RIGHT_FOREARM("right_forearm", "Right Forearm", MeasurementValueKind.LENGTH),
    ABDOMEN("abdomen", "Abdomen", MeasurementValueKind.LENGTH),
    HIPS("hips", "Hips", MeasurementValueKind.LENGTH),
    LEFT_THIGH("left_thigh", "Left Thigh", MeasurementValueKind.LENGTH),
    RIGHT_THIGH("right_thigh", "Right Thigh", MeasurementValueKind.LENGTH),
    LEFT_CALF("left_calf", "Left Calf", MeasurementValueKind.LENGTH),
    RIGHT_CALF("right_calf", "Right Calf", MeasurementValueKind.LENGTH);

    fun unitLabel(weightUnit: WeightUnit, lengthUnit: LengthUnit): String = when (kind) {
        MeasurementValueKind.WEIGHT -> weightUnit.label
        MeasurementValueKind.LENGTH -> lengthUnit.label
        MeasurementValueKind.PERCENTAGE -> "%"
    }

    fun labelWithUnit(weightUnit: WeightUnit, lengthUnit: LengthUnit): String =
        "$label (${unitLabel(weightUnit, lengthUnit)})"

    companion object {
        fun fromKey(key: String): MeasurementMetric? =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) || it.name.equals(key, ignoreCase = true) }
    }
}

enum class DateRangePreset(val label: String) {
    THIS_WEEK("This Week"),
    LAST_WEEK("Last Week"),
    THIS_MONTH("This Month"),
    LAST_3_MONTHS("3 Months"),
    LAST_YEAR("1 Year"),
    CUSTOM("Custom")
}

data class DateRangeSelection(
    val preset: DateRangePreset = DateRangePreset.THIS_WEEK,
    val customStartEpochDay: Long? = null,
    val customEndEpochDay: Long? = null
)

data class ResolvedDateRange(
    val selection: DateRangeSelection,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val startInclusiveMillis: Long,
    val endExclusiveMillis: Long,
    val formattedLabel: String
) {
    fun contains(epochMillis: Long): Boolean =
        epochMillis >= startInclusiveMillis && epochMillis < endExclusiveMillis
}

object DateRangeCalculator {
    private val sameYearStartFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val fullDateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    fun resolve(
        selection: DateRangeSelection,
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ResolvedDateRange {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val (rawStart, rawEnd) = when (selection.preset) {
            DateRangePreset.THIS_WEEK -> {
                val mon = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val sun = mon.plusDays(6)
                mon to sun
            }
            DateRangePreset.LAST_WEEK -> {
                val thisMon = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val lastMon = thisMon.minusWeeks(1)
                val lastSun = lastMon.plusDays(6)
                lastMon to lastSun
            }
            DateRangePreset.THIS_MONTH -> {
                val first = today.withDayOfMonth(1)
                val last = today.with(TemporalAdjusters.lastDayOfMonth())
                first to last
            }
            DateRangePreset.LAST_3_MONTHS -> {
                today.minusDays(89) to today
            }
            DateRangePreset.LAST_YEAR -> {
                today.minusDays(364) to today
            }
            DateRangePreset.CUSTOM -> {
                val a = selection.customStartEpochDay?.let { LocalDate.ofEpochDay(it) } ?: today.minusDays(6)
                val b = selection.customEndEpochDay?.let { LocalDate.ofEpochDay(it) } ?: today
                if (a.isAfter(b)) b to a else a to b
            }
        }
        val startInclusive = rawStart.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endExclusive = rawEnd.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val label = formatDateSpan(rawStart, rawEnd)
        return ResolvedDateRange(
            selection = selection,
            startDate = rawStart,
            endDate = rawEnd,
            startInclusiveMillis = startInclusive,
            endExclusiveMillis = endExclusive,
            formattedLabel = label
        )
    }

    fun formatDateSpan(start: LocalDate, end: LocalDate): String {
        return when {
            start == end -> start.format(fullDateFmt)
            start.year == end.year -> "${start.format(sameYearStartFmt)} - ${end.format(fullDateFmt)}"
            else -> "${start.format(fullDateFmt)} - ${end.format(fullDateFmt)}"
        }
    }
}

object UnitConverter {
    const val GRAMS_PER_KG = 1000.0
    const val GRAMS_PER_LB = 453.59237
    const val MAX_WEIGHT_GRAMS = 1_000_000L // 1000 kg / ~2204 lb safety cap
    const val MAX_BODY_WEIGHT_GRAMS = 500_000L // 500 kg safety cap for body weight
    const val MIN_BODY_WEIGHT_GRAMS = 10_000L // 10 kg min body weight
    const val MAX_REPS = 1000
    const val MAX_REST_SECONDS = 3600

    // Canonical length unit: tenths of a millimeter (decimillimeters, dmm)
    // 1 cm = 100 dmm; 1 in = 25.4 mm = 254 dmm (exact integer!)
    const val DMM_PER_CM = 100.0
    const val DMM_PER_IN = 254.0
    const val MIN_LENGTH_DMM = 500L     // 5 cm (~2 in)
    const val MAX_LENGTH_DMM = 30_000L  // 300 cm (~118 in)

    // Canonical percentage: basis points (1% = 100 bp)
    const val BP_PER_PERCENT = 100.0
    const val MIN_BODY_FAT_BP = 100L    // 1.0%
    const val MAX_BODY_FAT_BP = 7500L   // 75.0%

    /**
     * Parses a user weight input string in the given [unit] into canonical integer grams.
     * Returns null if blank, non-numeric, negative, NaN/Infinite, or above [MAX_WEIGHT_GRAMS].
     */
    fun parseWeightToGrams(input: String, unit: WeightUnit): Long? {
        val cleaned = input.trim().replace(',', '.')
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value < 0.0) return null

        val grams = when (unit) {
            WeightUnit.KG -> (value * GRAMS_PER_KG).roundToLong()
            WeightUnit.LB -> (value * GRAMS_PER_LB).roundToLong()
        }
        if (grams < 0L || grams > MAX_WEIGHT_GRAMS) return null
        return grams
    }

    /**
     * Parses an integer repetition count. Returns null if blank, negative, decimal, or > [MAX_REPS].
     */
    fun parseReps(input: String): Int? {
        val cleaned = input.trim()
        if (cleaned.isEmpty()) return null
        val reps = cleaned.toIntOrNull() ?: return null
        if (reps < 0 || reps > MAX_REPS) return null
        return reps
    }

    /**
     * Parses a manual rest timer duration in integer seconds.
     * Supports 0 (Off) or 1..3600 seconds. Returns null if invalid.
     */
    fun parseRestSeconds(input: String): Int? {
        val cleaned = input.trim()
        if (cleaned.isEmpty()) return null
        val seconds = cleaned.toIntOrNull() ?: return null
        if (seconds < 0 || seconds > MAX_REST_SECONDS) return null
        return seconds
    }

    fun formatRestSecondsHint(seconds: Int): String {
        if (seconds <= 0) return "Off"
        val mins = seconds / 60
        val rem = seconds % 60
        return String.format(Locale.US, "%d:%02d", mins, rem)
    }

    /**
     * Converts integer grams to a display value rounded to at most 1 decimal place (or 2 if needed for 0.25 increments),
     * stripping trailing zeros for clean text field editing and display.
     */
    fun gramsToDisplayValue(grams: Long, unit: WeightUnit): BigDecimal {
        val raw = when (unit) {
            WeightUnit.KG -> grams.toDouble() / GRAMS_PER_KG
            WeightUnit.LB -> grams.toDouble() / GRAMS_PER_LB
        }
        val scale = if (unit == WeightUnit.KG && grams % 250L == 0L && grams % 500L != 0L) 2 else 1
        return BigDecimal.valueOf(raw).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros()
    }

    fun formatGramsForInput(grams: Long?, unit: WeightUnit): String {
        if (grams == null) return ""
        val bd = gramsToDisplayValue(grams, unit)
        return bd.toPlainString()
    }

    fun formatGramsWithUnit(grams: Long, unit: WeightUnit): String {
        return "${formatGramsForInput(grams, unit)} ${unit.label}"
    }

    fun formatVolumeWithUnit(volumeGrams: Long, unit: WeightUnit): String {
        val raw = when (unit) {
            WeightUnit.KG -> volumeGrams.toDouble() / GRAMS_PER_KG
            WeightUnit.LB -> volumeGrams.toDouble() / GRAMS_PER_LB
        }
        val rounded = raw.roundToLong()
        return String.format(Locale.US, "%,d %s", rounded, unit.label)
    }

    // Body Measurement conversions
    fun parseLengthToDecimillimeters(input: String, unit: LengthUnit): Long? {
        val cleaned = input.trim().replace(',', '.')
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value <= 0.0) return null
        val dmm = when (unit) {
            LengthUnit.CM -> (value * DMM_PER_CM).roundToLong()
            LengthUnit.IN -> (value * DMM_PER_IN).roundToLong()
        }
        if (dmm < MIN_LENGTH_DMM || dmm > MAX_LENGTH_DMM) return null
        return dmm
    }

    fun decimillimetersToDisplayValue(dmm: Long, unit: LengthUnit): BigDecimal {
        val raw = when (unit) {
            LengthUnit.CM -> dmm.toDouble() / DMM_PER_CM
            LengthUnit.IN -> dmm.toDouble() / DMM_PER_IN
        }
        return BigDecimal.valueOf(raw).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros()
    }

    fun formatDecimillimetersForInput(dmm: Long?, unit: LengthUnit): String {
        if (dmm == null) return ""
        return decimillimetersToDisplayValue(dmm, unit).toPlainString()
    }

    fun formatDecimillimetersWithUnit(dmm: Long, unit: LengthUnit): String {
        return "${formatDecimillimetersForInput(dmm, unit)} ${unit.label}"
    }

    fun parseBodyFatToBasisPoints(input: String): Long? {
        val cleaned = input.trim().replace(',', '.')
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value <= 0.0) return null
        val bp = (value * BP_PER_PERCENT).roundToLong()
        if (bp < MIN_BODY_FAT_BP || bp > MAX_BODY_FAT_BP) return null
        return bp
    }

    fun basisPointsToDisplayValue(bp: Long): BigDecimal {
        val raw = bp.toDouble() / BP_PER_PERCENT
        return BigDecimal.valueOf(raw).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros()
    }

    fun formatBasisPointsForInput(bp: Long?): String {
        if (bp == null) return ""
        return basisPointsToDisplayValue(bp).toPlainString()
    }

    fun formatBasisPointsWithUnit(bp: Long): String {
        return "${formatBasisPointsForInput(bp)}%"
    }

    /**
     * Computes Lean Body Mass in canonical grams from [weightGrams] and [bodyFatBasisPoints].
     * Formula: weight * (1 - bodyFat / 100)
     */
    fun computeLeanBodyMassGrams(weightGrams: Long, bodyFatBasisPoints: Long): Long {
        val clampedBf = bodyFatBasisPoints.coerceIn(0L, 10_000L)
        return ((weightGrams * (10_000L - clampedBf)) + 5_000L) / 10_000L
    }

    fun parseMeasurementValue(
        metric: MeasurementMetric,
        input: String,
        weightUnit: WeightUnit,
        lengthUnit: LengthUnit
    ): Long? {
        return when (metric.kind) {
            MeasurementValueKind.WEIGHT -> {
                val g = parseWeightToGrams(input, weightUnit) ?: return null
                if (g < MIN_BODY_WEIGHT_GRAMS || g > MAX_BODY_WEIGHT_GRAMS) null else g
            }
            MeasurementValueKind.LENGTH -> parseLengthToDecimillimeters(input, lengthUnit)
            MeasurementValueKind.PERCENTAGE -> parseBodyFatToBasisPoints(input)
        }
    }

    fun formatMeasurementForInput(
        metric: MeasurementMetric,
        canonicalValue: Long?,
        weightUnit: WeightUnit,
        lengthUnit: LengthUnit
    ): String {
        if (canonicalValue == null) return ""
        return when (metric.kind) {
            MeasurementValueKind.WEIGHT -> formatGramsForInput(canonicalValue, weightUnit)
            MeasurementValueKind.LENGTH -> formatDecimillimetersForInput(canonicalValue, lengthUnit)
            MeasurementValueKind.PERCENTAGE -> formatBasisPointsForInput(canonicalValue)
        }
    }

    fun formatMeasurementWithUnit(
        metric: MeasurementMetric,
        canonicalValue: Long,
        weightUnit: WeightUnit,
        lengthUnit: LengthUnit
    ): String {
        return when (metric.kind) {
            MeasurementValueKind.WEIGHT -> formatGramsWithUnit(canonicalValue, weightUnit)
            MeasurementValueKind.LENGTH -> formatDecimillimetersWithUnit(canonicalValue, lengthUnit)
            MeasurementValueKind.PERCENTAGE -> formatBasisPointsWithUnit(canonicalValue)
        }
    }

    fun measurementToDisplayFloat(
        metric: MeasurementMetric,
        canonicalValue: Long,
        weightUnit: WeightUnit,
        lengthUnit: LengthUnit
    ): Float {
        return when (metric.kind) {
            MeasurementValueKind.WEIGHT -> gramsToDisplayValue(canonicalValue, weightUnit).toFloat()
            MeasurementValueKind.LENGTH -> decimillimetersToDisplayValue(canonicalValue, lengthUnit).toFloat()
            MeasurementValueKind.PERCENTAGE -> basisPointsToDisplayValue(canonicalValue).toFloat()
        }
    }
}

data class SetSample(
    val weightGrams: Long?,
    val reps: Int?,
    val isComplete: Boolean,
    val setType: SetType,
    val exerciseType: String = "weight_reps",
    val durationSeconds: Int? = null,
    val distanceMeters: Long? = null
)

data class PersonalRecord(
    val weightGrams: Long,
    val reps: Int
)

object WorkoutMetricsCalculator {

    /**
     * Qualifying sets for volume and completed work set counts:
     * - Must be marked [isComplete] = true
     * - Must be [SetType.WORK] (warm-up sets are excluded from main volume by default per spec)
     * - Must have non-null finite weightGrams >= 0 and reps >= 0
     */
    fun isQualifyingWorkSet(sample: SetSample): Boolean {
        if (!sample.isComplete || sample.setType != SetType.WORK) return false
        return when (sample.exerciseType) {
            "duration" -> (sample.durationSeconds ?: 0) > 0
            "distance_duration" -> (sample.durationSeconds ?: 0) > 0 && (sample.distanceMeters ?: -1) >= 0
            "bodyweight_reps" -> (sample.reps ?: -1) >= 0 && (sample.weightGrams ?: 0) >= 0
            "weight_reps", "assisted_bodyweight" -> (sample.weightGrams ?: -1) >= 0 && (sample.reps ?: -1) >= 0
            else -> false
        }
    }

    fun calculateTotalVolumeGrams(sets: Iterable<SetSample>): Long {
        var total = 0L
        for (s in sets) {
            if (isQualifyingWorkSet(s) && s.exerciseType in listOf("weight_reps", "bodyweight_reps")) {
                total += (s.weightGrams ?: 0L) * (s.reps ?: 0).toLong()
            }
        }
        return total
    }

    fun calculateTotalVolumeGramsFromEntities(sets: Iterable<LoggedSetEntity>): Long {
        return calculateTotalVolumeGrams(
            sets.map {
                SetSample(
                    weightGrams = it.weightGrams,
                    reps = it.reps,
                    isComplete = it.isComplete,
                    setType = it.setType,
                    exerciseType = it.exerciseType,
                    durationSeconds = it.durationSeconds,
                    distanceMeters = it.distanceMeters
                )
            }
        )
    }

    fun countCompletedWorkSets(sets: Iterable<SetSample>): Int {
        return sets.count { isQualifyingWorkSet(it) }
    }

    fun countCompletedWorkSetsFromEntities(sets: Iterable<LoggedSetEntity>): Int {
        return sets.count {
            isQualifyingWorkSet(SetSample(it.weightGrams, it.reps, it.isComplete, it.setType, it.exerciseType, it.durationSeconds, it.distanceMeters))
        }
    }

    /**
     * Personal record definition:
     * Highest logged weightGrams among completed WORK sets with reps >= 1.
     * Ties in weightGrams are broken by highest reps.
     */
    fun findPersonalRecord(sets: Iterable<SetSample>): PersonalRecord? {
        return sets
            .filter { isQualifyingWorkSet(it) && (it.reps ?: 0) >= 1 && (it.exerciseType == "weight_reps" || (it.exerciseType == "bodyweight_reps" && (it.weightGrams ?: 0) > 0)) }
            .maxWithOrNull(
                compareBy<SetSample> { it.weightGrams ?: 0L }
                    .thenBy { it.reps ?: 0 }
            )?.let {
                PersonalRecord(weightGrams = it.weightGrams!!, reps = it.reps!!)
            }
    }

    /**
     * Returns epoch millis for Monday 00:00:00.000 of the calendar week containing [nowMillis] in [zoneId].
     */
    fun startOfIsoWeekMillis(nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        val zdt = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val mondayStart = zdt
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toLocalDate()
            .atStartOfDay(zoneId)
        return mondayStart.toInstant().toEpochMilli()
    }

    fun formatDurationSeconds(seconds: Long): String {
        val safeSec = seconds.coerceAtLeast(0L)
        val hrs = safeSec / 3600
        val mins = (safeSec % 3600) / 60
        val secs = safeSec % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun formatSessionDuration(startedAt: Long, endedAt: Long?): String {
        val end = endedAt ?: startedAt
        val totalMinutes = ((end - startedAt).coerceAtLeast(0L)) / 60_000L
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            totalMinutes == 0L && end > startedAt -> "< 1m"
            else -> "${mins}m"
        }
    }
}
