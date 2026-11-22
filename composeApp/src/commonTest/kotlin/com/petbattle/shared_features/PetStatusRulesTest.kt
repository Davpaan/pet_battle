package com.petbattle.shared_features

import com.petbattle.shared_features.pet_status.domain.PetCombatEffects
import com.petbattle.shared_features.pet_status.domain.PetMood
import com.petbattle.shared_features.pet_status.domain.PetStatusBalance
import com.petbattle.shared_features.pet_status.domain.PetStatusRules
import com.petbattle.shared_features.pet_status.domain.entities.PetStatusEntity
import com.petbattle.shared_features.pet_status.domain.mood
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun close(expected: Double, actual: Double, eps: Double = 1e-6) =
    assertTrue(abs(expected - actual) <= eps, "expected $expected but was $actual")

class PetStatusRulesTest {
    private val t0 = 1_767_268_800_000L // 2026-01-01T12:00Z
    private fun status(sleeping: Boolean = false, stats: Double = 80.0) = PetStatusEntity(
        satiety = stats, energy = stats, happiness = stats, hygiene = stats,
        isSleeping = sleeping, lastUpdatedAt = t0,
    )
    private val minute = 60_000L
    private val second = 1_000L

    @Test fun decaysProportionallyToElapsedTime() {
        val r = PetStatusRules.advanceTo(status(), t0 + 5 * minute)
        val hours = 5.0 / 60
        close(80 - PetStatusBalance.SATIETY_DECAY_PER_HOUR * hours, r.satiety)
        close(80 - PetStatusBalance.HYGIENE_DECAY_PER_HOUR * hours, r.hygiene)
        assertEquals(t0 + 5 * minute, r.lastUpdatedAt)
    }

    @Test fun energyNeverDropsWithTime() {
        val r = PetStatusRules.advanceTo(status(stats = 60.0), t0 + 3L * 24 * 3600 * 1000)
        assertEquals(60.0, r.energy)
        assertEquals(PetStatusBalance.MIN_STAT, r.satiety)
    }

    @Test fun clockGoingBackwardsDoesNotAlterStats() {
        val r = PetStatusRules.advanceTo(status(), t0 - 3_600_000)
        assertEquals(80.0, r.satiety)
        assertEquals(t0 - 3_600_000, r.lastUpdatedAt)
    }

    @Test fun sleepingPetIsRestedInTenSecondsAndWakesUp() {
        var s = status(sleeping = true).copy(energy = 0.0)
        for (sec in 1..9) s = PetStatusRules.advanceTo(s, t0 + sec * second)
        assertTrue(s.isSleeping)
        close(90.0, s.energy)
        s = PetStatusRules.advanceTo(s, t0 + 10 * second)
        assertEquals(PetStatusBalance.MAX_STAT, s.energy)
        assertFalse(s.isSleeping)
    }

    @Test fun oneOfflineJumpMatchesManyLiveTicks() {
        val end = t0 + 2 * 3_600_000L
        val offline = PetStatusRules.advanceTo(status(stats = 30.0), end)
        var live = status(stats = 30.0)
        for (s in 1..2 * 3600) live = PetStatusRules.advanceTo(live, t0 + s * second)
        close(live.satiety, offline.satiety)
        close(live.happiness, offline.happiness, 0.5)
    }

    @Test fun actionsRespectSleepAndCaps() {
        assertEquals(PetStatusBalance.MAX_STAT, PetStatusRules.feed(status(stats = 90.0)).satiety)
        val asleep = status(sleeping = true)
        assertEquals(asleep, PetStatusRules.feed(asleep))
        assertEquals(asleep, PetStatusRules.clean(asleep))
        assertEquals(asleep, PetStatusRules.cuddle(asleep))
        assertEquals(52.0, PetStatusRules.cuddle(status(stats = 50.0)).happiness)
        assertEquals(79.0, PetStatusRules.spendEnergy(status(), 1.0).energy)
        assertEquals(0.0, PetStatusRules.spendEnergy(status(stats = 0.5), 1.0).energy)
    }

    @Test fun moodReflectsStatus() {
        assertEquals(PetMood.sleeping, status(sleeping = true).mood)
        assertEquals(PetMood.hungry, status().copy(satiety = 10.0).mood)
        assertEquals(PetMood.tired, status().copy(energy = 10.0).mood)
        assertEquals(PetMood.happy, status(stats = 90.0).mood)
    }

    @Test fun combatEffectsAreProportional() {
        val s = status(stats = 100.0)
        assertEquals(1.0, PetCombatEffects.attackSpeed(s))
        assertEquals(0.7, PetCombatEffects.fireInterval(s, 0.7))
        close(1.4, PetCombatEffects.fireInterval(s.copy(energy = 50.0), 0.7)!!)
        assertNull(PetCombatEffects.fireInterval(s.copy(energy = 0.0), 0.7))
        assertEquals(0.25, PetCombatEffects.damage(s.copy(satiety = 25.0)))
        assertEquals(0.7, PetCombatEffects.hitChance(s.copy(happiness = 70.0)))
    }
}
