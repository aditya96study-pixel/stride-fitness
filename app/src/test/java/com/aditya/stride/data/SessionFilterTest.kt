package com.aditya.stride.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionFilterTest {

    private fun session(
        id: Long,
        distanceM: Double,
        type: ActivityType,
        epochDay: Long = 20_000,
        kcal: Int = 100,
    ) = RunSession(
        id = id,
        epochDay = epochDay,
        startTime = 0,
        endTime = 0,
        distanceM = distanceM,
        movingTimeMs = 0,
        elapsedTimeMs = 0,
        avgSpeedMps = 0.0,
        maxSpeedMps = 0.0,
        elevationGainM = 0.0,
        kcalAuto = kcal,
        kcal = kcal,
        activityType = type.name,
    )

    private val sessions = listOf(
        session(1, 5_040.0, ActivityType.RUN),
        session(2, 2_010.0, ActivityType.WALK, epochDay = 20_001),
        session(3, 6_000.0, ActivityType.TREADMILL, epochDay = 20_001),
        session(4, 5_000.0, ActivityType.WALK),
    )

    @Test
    fun anEmptyFilterShowsEverything() {
        val all = SessionFilter()
        assertTrue(all.isEmpty)
        assertEquals(sessions, sessions.matching(all))
    }

    @Test
    fun typesAreOredWithinTheDimension() {
        val f = SessionFilter(types = setOf(ActivityType.WALK, ActivityType.TREADMILL))
        assertEquals(listOf(2L, 3L, 4L), sessions.matching(f).map { it.id })
    }

    @Test
    fun typeAndBucketAreAndedAcrossDimensions() {
        val f = SessionFilter(types = setOf(ActivityType.WALK), buckets = setOf(RaceBucket.K5))
        assertEquals(listOf(4L), sessions.matching(f).map { it.id })
    }

    @Test
    fun bucketWindowsAreDisjointSoEveryDistanceHasAtMostOneBucket() {
        val windows = RaceBucket.entries.map {
            it.targetM - it.toleranceM..it.targetM + it.toleranceM
        }
        for (i in windows.indices) {
            for (j in i + 1 until windows.size) {
                assertTrue(
                    "${RaceBucket.entries[i]} overlaps ${RaceBucket.entries[j]}",
                    windows[i].endInclusive < windows[j].start,
                )
            }
        }
    }

    @Test
    fun theOneKilometreBucketUsesTheFiftyMetreFloorNotTwoPercent() {
        assertEquals(RaceBucket.K1, bucketOf(1_049.0))
        assertNull(bucketOf(1_051.0))
    }

    @Test
    fun theTenKilometreBucketUsesTwoPercent() {
        assertEquals(RaceBucket.K10, bucketOf(9_810.0))
        assertNull(bucketOf(9_790.0))
    }

    @Test
    fun anUntidyDistanceBelongsToNoBucket() {
        assertNull(bucketOf(7_400.0))
        assertNull(bucketOf(0.0))
    }

    @Test
    fun togglingAChipTwiceReturnsToTheDefault() {
        val once = SessionFilter().toggleType(ActivityType.WALK).toggleBucket(RaceBucket.K5)
        assertTrue(once.matches(session(9, 5_000.0, ActivityType.WALK)))
        val twice = once.toggleType(ActivityType.WALK).toggleBucket(RaceBucket.K5)
        assertTrue(twice.isEmpty)
    }

    @Test
    fun dailyTotalsGroupByDayAndSortAscending() {
        val km = sessions.dailyDistanceKm()
        assertEquals(listOf(20_000L, 20_001L), km.map { it.epochDay })
        assertEquals(10.04, km[0].value, 1e-9)
        assertEquals(8.01, km[1].value, 1e-9)

        val kcal = sessions.dailyKcal()
        assertEquals(200.0, kcal[0].value, 1e-9)
        assertEquals(200.0, kcal[1].value, 1e-9)
    }

    @Test
    fun anUnknownActivityStringReadsAsARunRatherThanThrowing() {
        val broken = session(9, 5_000.0, ActivityType.RUN).copy(activityType = "JETPACK")
        assertEquals(ActivityType.RUN, broken.type)
    }
}
