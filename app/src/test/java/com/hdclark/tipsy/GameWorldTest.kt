package com.hdclark.tipsy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameWorldTest {
    @Test
    fun `ball generation is deterministic for same seed`() {
        val a = GameWorld(10f, 16f, seed = 77)
        val b = GameWorld(10f, 16f, seed = 77)

        val aSpecs = a.snapshotBallSpecs()
        val bSpecs = b.snapshotBallSpecs()

        assertEquals(10, aSpecs.size)
        assertEquals(aSpecs.map { it.first }, bSpecs.map { it.first })
        assertEquals(aSpecs.map { "%.5f".format(it.second) }, bSpecs.map { "%.5f".format(it.second) })
        assertEquals(
            aSpecs.map { "%.4f,%.4f".format(it.third.x, it.third.y) },
            bSpecs.map { "%.4f,%.4f".format(it.third.x, it.third.y) }
        )
    }

    @Test
    fun `race state finishes when three balls complete target laps`() {
        val raceState = RaceState(trackLength = 100f, targetLaps = 1, podiumSize = 3, ballCount = 10)

        raceState.update(FloatArray(10) { if (it < 3) 95f else 10f })
        val update = raceState.update(FloatArray(10) { if (it < 3) 5f else 10f })

        assertTrue(update.raceFinishedNow)
        assertEquals(listOf(0, 1, 2), raceState.podium)
    }
}
