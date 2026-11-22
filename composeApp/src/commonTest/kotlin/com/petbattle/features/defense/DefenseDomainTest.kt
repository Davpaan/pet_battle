package com.petbattle.features.defense

import com.petbattle.features.defense.domain.CombatRules
import com.petbattle.features.defense.domain.DefenseBalance
import com.petbattle.features.defense.domain.DefenseRules
import com.petbattle.features.defense.domain.RoutePath
import com.petbattle.features.defense.domain.entities.EnemyEntity
import com.petbattle.features.defense.domain.entities.MapPoint
import com.petbattle.features.defense.domain.entities.MapRoute
import com.petbattle.features.defense.domain.maps.jungleCampMap
import com.petbattle.shared_features.pet_skills.domain.PetSkillsCatalog
import com.petbattle.shared_features.pet_skills.domain.entities.KnockbackEffect
import com.petbattle.shared_features.pet_skills.domain.entities.SlowEffect
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun close(expected: Double, actual: Double, eps: Double = 1e-9) =
    assertTrue(abs(expected - actual) <= eps, "expected $expected but was $actual")

class DefenseDomainTest {
    @Test fun speedAndEnemyLevelsGrowWithTime() {
        assertEquals(listOf(0, 0, 1, 2), listOf(0.0, 9.99, 10.0, 25.0).map(DefenseRules::speedLevel))
        close(1.6, DefenseRules.speedMultiplier(30.0))
        assertEquals(listOf(1, 1, 2, 4), listOf(0.0, 19.9, 20.0, 65.0).map(DefenseRules::enemyLevel))
    }

    @Test fun routePathInterpolates() {
        val p = RoutePath(MapRoute("l", listOf(MapPoint(0.0, 0.0), MapPoint(10.0, 0.0), MapPoint(10.0, 10.0))))
        assertEquals(20.0, p.length)
        assertEquals(MapPoint(5.0, 0.0), p.positionAt(5.0))
        assertEquals(MapPoint(10.0, 5.0), p.positionAt(15.0))
        assertEquals(MapPoint(10.0, 10.0), p.positionAt(99.0))
    }

    @Test fun mapPlacementRules() {
        val r = DefenseBalance.PET_FOOTPRINT_RADIUS
        assertTrue(jungleCampMap.canPlace(MapPoint(1000.0, 540.0), r))
        assertFalse(jungleCampMap.canPlace(MapPoint(700.0, 430.0), r)) // path
        assertFalse(jungleCampMap.canPlace(MapPoint(1040.0, 260.0), r)) // hut
        for (route in jungleCampMap.routes) {
            val path = RoutePath(route)
            var d = 0.0
            while (d <= path.length) {
                assertFalse(jungleCampMap.canPlace(path.positionAt(d), r), "${route.name} at $d")
                d += 8
            }
        }
    }

    @Test fun enemiesHave1point2HpPerLevel() {
        close(1.2, EnemyEntity.spawn(1).hp)
        close(3.6, EnemyEntity.spawn(3).hp)
        assertEquals(1, EnemyEntity.spawn(0).level)
        var e = EnemyEntity.spawn(3)
        repeat(3) { e = e.hit(1.2) }
        assertTrue(e.isDefeated)
        for (n in 1..10) {
            assertTrue(EnemyEntity.spawn(n).hit(DefenseRules.petDamage(n)).isDefeated)
            assertFalse(EnemyEntity.spawn(n + 1).hit(DefenseRules.petDamage(n)).isDefeated)
        }
    }

    @Test fun statusScalesCombat() {
        val base = PetEntity.newborn("A", PetSpecies.crow, 0)
        fun pet(level: Int = 1, energy: Double = 100.0, satiety: Double = 100.0, happiness: Double = 100.0) =
            base.copy(
                skills = base.skills.copy(level = level),
                status = base.status.copy(energy = energy, satiety = satiety, happiness = happiness),
            )
        close(2.4, DefenseRules.shotDamage(pet(level = 2)))
        close(1.2, DefenseRules.shotDamage(pet(level = 2, satiety = 50.0)))
        close(DefenseBalance.PET_FIRE_INTERVAL * 4, DefenseRules.fireInterval(pet(energy = 25.0))!!)
        assertNull(DefenseRules.fireInterval(pet(energy = 0.0)))
        assertTrue(DefenseRules.rollHit(pet(happiness = 30.0), 0.29))
        assertFalse(DefenseRules.rollHit(pet(happiness = 30.0), 0.31))
    }

    @Test fun elementalEffects() {
        // Earth: plain hit.
        val earth = CombatRules.applyHit(EnemyEntity.spawn(3), 1.2, PetSkillsCatalog.earthRock.effect, "p")
        close(2.4, earth.enemy.hp)
        // Fire: nothing instant, 1.2x over 3 s, credited to the burner.
        var fire = CombatRules.applyHit(EnemyEntity.spawn(10), 2.0, PetSkillsCatalog.fireBurn.effect, "kai").enemy
        close(12.0, fire.hp)
        fire = fire.tick(1.5)
        close(12 - 1.2, fire.hp)
        fire = fire.tick(1.5)
        close(12 - 2.4, fire.hp)
        assertFalse(fire.isBurning)
        val burnt = CombatRules.applyHit(EnemyEntity.spawn(1), 1.0, PetSkillsCatalog.fireBurn.effect, "kai")
            .enemy.tick(3.0)
        assertTrue(burnt.isDefeated)
        assertEquals("kai", burnt.lastHitBy)
        // Water: damage + slow that wears off.
        val slow = PetSkillsCatalog.waterSlow.effect as SlowEffect
        var wet = CombatRules.applyHit(EnemyEntity.spawn(3), 1.2, slow, "p").enemy
        close(2.4, wet.hp)
        assertEquals(slow.speedFactor, wet.speedFactor)
        wet = wet.tick(slow.seconds + 0.1)
        assertEquals(1.0, wet.speedFactor)
        // Air: damage + knockback.
        val air = CombatRules.applyHit(EnemyEntity.spawn(3), 1.2, PetSkillsCatalog.airGust.effect, "p")
        close(2.4, air.enemy.hp)
        assertEquals((PetSkillsCatalog.airGust.effect as KnockbackEffect).distance, air.knockback)
    }
}
