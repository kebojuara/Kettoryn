package com.williamchandra.kettoryn.domain

import com.williamchandra.kettoryn.data.local.SetType
import com.williamchandra.kettoryn.data.preferences.LengthUnit
import com.williamchandra.kettoryn.data.preferences.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class WorkoutCalculatorsTest {

    @Test
    fun `kg to grams and back preserves exact value`() {
        val grams = UnitConverter.parseWeightToGrams("62.5", WeightUnit.KG)
        assertEquals(62500L, grams)
        assertEquals("62.5", UnitConverter.formatGramsForInput(grams!!, WeightUnit.KG))
        assertEquals("62.5 kg", UnitConverter.formatGramsWithUnit(grams, WeightUnit.KG))
    }

    @Test
    fun `lb to grams and back rounds without drift`() {
        val grams135 = UnitConverter.parseWeightToGrams("135", WeightUnit.LB)
        assertEquals(61235L, grams135)
        assertEquals("135", UnitConverter.formatGramsForInput(grams135!!, WeightUnit.LB))

        val grams100kg = 100_000L
        assertEquals("220.5", UnitConverter.formatGramsForInput(grams100kg, WeightUnit.LB))
        assertEquals("100", UnitConverter.formatGramsForInput(grams100kg, WeightUnit.KG))
    }

    @Test
    fun `invalid or negative weight and reps return null`() {
        assertNull(UnitConverter.parseWeightToGrams("", WeightUnit.KG))
        assertNull(UnitConverter.parseWeightToGrams("   ", WeightUnit.KG))
        assertNull(UnitConverter.parseWeightToGrams("-10", WeightUnit.KG))
        assertNull(UnitConverter.parseWeightToGrams("abc", WeightUnit.KG))
        assertNull(UnitConverter.parseWeightToGrams("99999", WeightUnit.KG))

        assertNull(UnitConverter.parseReps(""))
        assertNull(UnitConverter.parseReps("-1"))
        assertNull(UnitConverter.parseReps("12.5"))
        assertNull(UnitConverter.parseReps("50000"))
        assertEquals(0, UnitConverter.parseReps("0"))
        assertEquals(8, UnitConverter.parseReps(" 8 "))
    }

    @Test
    fun `zero weight is valid for bodyweight sets`() {
        val grams = UnitConverter.parseWeightToGrams("0", WeightUnit.KG)
        assertEquals(0L, grams)
    }

    @Test
    fun `volume calculation includes only completed work sets and excludes warmups and incomplete sets`() {
        val sets = listOf(
            SetSample(weightGrams = 60_000L, reps = 8, isComplete = true, setType = SetType.WARMUP),
            SetSample(weightGrams = 60_000L, reps = 8, isComplete = true, setType = SetType.WORK),
            SetSample(weightGrams = 65_000L, reps = 6, isComplete = true, setType = SetType.WORK),
            SetSample(weightGrams = 70_000L, reps = 4, isComplete = true, setType = SetType.WORK),
            SetSample(weightGrams = 80_000L, reps = 5, isComplete = false, setType = SetType.WORK),
            SetSample(weightGrams = null, reps = 5, isComplete = true, setType = SetType.WORK),
            SetSample(weightGrams = 70_000L, reps = null, isComplete = true, setType = SetType.WORK)
        )
        val volumeGrams = WorkoutMetricsCalculator.calculateTotalVolumeGrams(sets)
        assertEquals(1_150_000L, volumeGrams)
        assertEquals(3, WorkoutMetricsCalculator.countCompletedWorkSets(sets))
    }

    @Test
    fun `personal record selects highest weight for completed work set with at least 1 rep and breaks ties by reps`() {
        val sets = listOf(
            SetSample(weightGrams = 100_000L, reps = 5, isComplete = false, setType = SetType.WORK),
            SetSample(weightGrams = 110_000L, reps = 3, isComplete = true, setType = SetType.WARMUP),
            SetSample(weightGrams = 95_000L, reps = 0, isComplete = true, setType = SetType.WORK),
            SetSample(weightGrams = 80_000L, reps = 5, isComplete = true, setType = SetType.WORK),
            SetSample(weightGrams = 85_000L, reps = 3, isComplete = true, setType = SetType.WORK),
            SetSample(weightGrams = 85_000L, reps = 5, isComplete = true, setType = SetType.WORK)
        )
        val pr = WorkoutMetricsCalculator.findPersonalRecord(sets)
        assertEquals(85_000L, pr?.weightGrams)
        assertEquals(5, pr?.reps)
    }

    @Test
    fun `calendar week start calculation uses Monday 00_00 in local timezone`() {
        val zone = ZoneId.of("Asia/Jakarta")
        val satInstantMillis = ZonedDateTime.of(2026, 10, 31, 14, 30, 0, 0, zone).toInstant().toEpochMilli()
        val expectedMonMillis = ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        val weekStart = WorkoutMetricsCalculator.startOfIsoWeekMillis(satInstantMillis, zone)
        assertEquals(expectedMonMillis, weekStart)
    }

    @Test
    fun `manual rest seconds parser and mm_ss hint handle off, arbitrary seconds, and invalid values`() {
        assertEquals(0, UnitConverter.parseRestSeconds("0"))
        assertEquals(75, UnitConverter.parseRestSeconds("75"))
        assertEquals(95, UnitConverter.parseRestSeconds(" 95 "))
        assertEquals(135, UnitConverter.parseRestSeconds("135"))
        assertEquals(3600, UnitConverter.parseRestSeconds("3600"))

        assertNull(UnitConverter.parseRestSeconds(""))
        assertNull(UnitConverter.parseRestSeconds("-5"))
        assertNull(UnitConverter.parseRestSeconds("90.5"))
        assertNull(UnitConverter.parseRestSeconds("3601"))
        assertNull(UnitConverter.parseRestSeconds("abc"))

        assertEquals("Off", UnitConverter.formatRestSecondsHint(0))
        assertEquals("1:15", UnitConverter.formatRestSecondsHint(75))
        assertEquals("1:35", UnitConverter.formatRestSecondsHint(95))
        assertEquals("2:15", UnitConverter.formatRestSecondsHint(135))
    }

    @Test
    fun `length unit cm and in convert to canonical decimillimeters without drift`() {
        val dmm80cm = UnitConverter.parseLengthToDecimillimeters("80.5", LengthUnit.CM)
        assertEquals(8050L, dmm80cm)
        assertEquals("80.5", UnitConverter.formatDecimillimetersForInput(dmm80cm, LengthUnit.CM))
        assertEquals("80.5 cm", UnitConverter.formatDecimillimetersWithUnit(dmm80cm!!, LengthUnit.CM))

        // 30 inches = 7620 dmm = 76.2 cm exact
        val dmm30in = UnitConverter.parseLengthToDecimillimeters("30", LengthUnit.IN)
        assertEquals(7620L, dmm30in)
        assertEquals("30", UnitConverter.formatDecimillimetersForInput(dmm30in, LengthUnit.IN))
        assertEquals("76.2", UnitConverter.formatDecimillimetersForInput(dmm30in, LengthUnit.CM))

        // Out of range rejected
        assertNull(UnitConverter.parseLengthToDecimillimeters("0", LengthUnit.CM))
        assertNull(UnitConverter.parseLengthToDecimillimeters("-10", LengthUnit.CM))
        assertNull(UnitConverter.parseLengthToDecimillimeters("500", LengthUnit.CM))
    }

    @Test
    fun `body fat percentage and derived lean body mass calculate accurately`() {
        val bfBp = UnitConverter.parseBodyFatToBasisPoints("15.5")
        assertEquals(1550L, bfBp)
        assertEquals("15.5%", UnitConverter.formatBasisPointsWithUnit(bfBp!!))

        // 80.0 kg (80,000 g) at 15.0% body fat (1500 bp) -> 68.0 kg (68,000 g) lean body mass
        val lbmGrams = UnitConverter.computeLeanBodyMassGrams(80_000L, 1500L)
        assertEquals(68_000L, lbmGrams)

        // All 17 metrics from 07-BODY-MEASUREMENTS.md exist and are unlocked
        assertEquals(17, MeasurementMetric.entries.size)
        assertEquals(MeasurementMetric.BODY_WEIGHT, MeasurementMetric.entries.first())
        assertEquals(MeasurementMetric.RIGHT_CALF, MeasurementMetric.entries.last())
    }

    @Test
    fun `date range calculator resolves presets and custom inclusive local boundaries`() {
        val zone = ZoneId.of("Asia/Jakarta")
        // Saturday Oct 31, 2026 14:30
        val nowMillis = ZonedDateTime.of(2026, 10, 31, 14, 30, 0, 0, zone).toInstant().toEpochMilli()

        val thisWeek = DateRangeCalculator.resolve(DateRangeSelection(DateRangePreset.THIS_WEEK), nowMillis, zone)
        assertEquals(LocalDate.of(2026, 10, 26), thisWeek.startDate)
        assertEquals(LocalDate.of(2026, 11, 1), thisWeek.endDate)
        assertEquals("Oct 26 - Nov 1, 2026", thisWeek.formattedLabel)

        // End of Nov 1 at 23:59:59.999 is included, Nov 2 00:00:00.000 is excluded
        val nov1Late = ZonedDateTime.of(2026, 11, 1, 23, 59, 59, 999_000_000, zone).toInstant().toEpochMilli()
        val nov2Start = ZonedDateTime.of(2026, 11, 2, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertTrue(thisWeek.contains(nov1Late))
        assertFalse(thisWeek.contains(nov2Start))

        val lastWeek = DateRangeCalculator.resolve(DateRangeSelection(DateRangePreset.LAST_WEEK), nowMillis, zone)
        assertEquals(LocalDate.of(2026, 10, 19), lastWeek.startDate)
        assertEquals(LocalDate.of(2026, 10, 25), lastWeek.endDate)

        // Custom range with reversed start/end normalizes automatically
        val customReversed = DateRangeCalculator.resolve(
            DateRangeSelection(
                preset = DateRangePreset.CUSTOM,
                customStartEpochDay = LocalDate.of(2026, 10, 20).toEpochDay(),
                customEndEpochDay = LocalDate.of(2026, 10, 10).toEpochDay()
            ),
            nowMillis,
            zone
        )
        assertEquals(LocalDate.of(2026, 10, 10), customReversed.startDate)
        assertEquals(LocalDate.of(2026, 10, 20), customReversed.endDate)
    }
}
