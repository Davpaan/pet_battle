package com.petbattle.shared_features

import com.petbattle.core.persistence.InMemoryKeyValueStore
import com.petbattle.shared_features.pet_skills.domain.PetElement
import com.petbattle.shared_features.pet_skills.domain.PetProgression
import com.petbattle.shared_features.pet_skills.domain.PetSkillsCatalog
import com.petbattle.shared_features.pet_skills.domain.PetType
import com.petbattle.shared_features.pet_skills.domain.element
import com.petbattle.shared_features.pet_skills.domain.entities.BurnEffect
import com.petbattle.shared_features.pet_skills.domain.entities.PetSkillEntity
import com.petbattle.shared_features.pet_skills.domain.entities.PetSkillsEntity
import com.petbattle.shared_features.pet_skills.domain.habitat
import com.petbattle.shared_features.pet_skills.domain.type
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_status.data.LocalPetRepository
import com.petbattle.shared_features.pet_status.domain.PetNameError
import com.petbattle.shared_features.pet_status.domain.RosterRules
import com.petbattle.shared_features.pet_status.domain.entities.PetRosterEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RosterAndSkillsTest {
    private val now = 1_767_268_800_000L
    private fun adopt(r: PetRosterEntity, s: PetSpecies, n: String) = RosterRules.adopt(r, s, n, now)

    @Test fun speciesTraits() {
        for (s in PetSpecies.entries) {
            val expected = when (s) {
                PetSpecies.crow -> PetType.darkness
                PetSpecies.whiteDove -> PetType.light
                else -> PetType.elemental
            }
            assertEquals(expected, s.type, s.name)
            assertEquals(s.habitat.element, s.element)
        }
        assertEquals(PetElement.fire, PetSpecies.cardinal.element)
        assertEquals(PetElement.water, PetSpecies.woodThrush.element)
        assertEquals(PetElement.earth, PetSpecies.crow.element)
        assertEquals(PetElement.air, PetSpecies.hummingbird.element)
    }

    @Test fun progressionLevelsUpAndCaps() {
        assertEquals(listOf(10, 20, 100, 0), listOf(0, 1, 9, 10).map(PetProgression::xpToNextLevel))
        var s = PetSkillsEntity.forSpecies(PetSpecies.cardinal)
        var kills = 0
        while (!PetProgression.isMaxLevel(s)) {
            s = PetProgression.gainXp(s, PetProgression.XP_PER_ENEMY); kills++
        }
        assertEquals(550, kills)
        assertEquals(s, PetProgression.gainXp(s, 5))
        val one = PetProgression.gainXp(PetSkillsEntity.forSpecies(PetSpecies.crow), 9)
        assertEquals(0 to 9, one.level to one.xp)
        val two = PetProgression.gainXp(one, 1)
        assertEquals(1 to 0, two.level to two.xp)
    }

    @Test fun skillsCatalog() {
        val sk = { s: PetSpecies -> PetSkillsEntity.forSpecies(s) }
        assertEquals(PetSkillsCatalog.fireBurn, PetSkillsCatalog.basicAttackFor(sk(PetSpecies.cardinal)))
        assertEquals(PetSkillsCatalog.waterSlow, PetSkillsCatalog.basicAttackFor(sk(PetSpecies.woodThrush)))
        assertEquals(PetSkillsCatalog.airGust, PetSkillsCatalog.basicAttackFor(sk(PetSpecies.hummingbird)))
        assertEquals(PetSkillsCatalog.earthRock, PetSkillsCatalog.basicAttackFor(sk(PetSpecies.crow)))
        assertEquals(BurnEffect(1.2, 3.0), PetSkillsCatalog.fireBurn.effect)

        val shadow = PetSkillEntity("shadow", "Sombra", "", 0)
        val crowOnly = PetSkillEntity("murder", "Bandada", "", 5)
        fun unlocked(s: PetSpecies, k: PetSkillsEntity) = PetSkillsCatalog.unlockedFor(
            s, k, elementTable = emptyMap(),
            typeTable = mapOf(PetType.darkness to listOf(shadow)),
            speciesTypeTable = mapOf((PetSpecies.crow to PetType.darkness) to listOf(crowOnly)),
        ).map { it.id }
        assertEquals(listOf("shadow"), unlocked(PetSpecies.crow, sk(PetSpecies.crow)))
        assertEquals(listOf("shadow", "murder"), unlocked(PetSpecies.crow, sk(PetSpecies.crow).copy(level = 5)))
        val darkMagpie = sk(PetSpecies.magpie).copy(type = PetType.darkness, level = 10)
        assertEquals(listOf("shadow"), unlocked(PetSpecies.magpie, darkMagpie))
    }

    @Test fun adoptionNeedsAValidUniqueName() {
        val roster = adopt(PetRosterEntity.empty, PetSpecies.crow, "  Kai  ")
        assertEquals("Kai", roster.active!!.name)
        assertEquals(PetNameError.empty, RosterRules.validateName(roster, "  "))
        assertEquals(PetNameError.taken, RosterRules.validateName(roster, "kai"))
        assertEquals(PetNameError.tooLong, RosterRules.validateName(roster, "x".repeat(17)))
        assertNull(RosterRules.validateName(roster, "Luna"))
        assertFalse(RosterRules.canAdopt(roster, PetSpecies.crow))
        assertFailsWith<IllegalArgumentException> { adopt(roster, PetSpecies.crow, "Otro") }
    }

    @Test fun eachPetKeepsItsOwnLevelAndStatus() {
        var r = adopt(PetRosterEntity.empty, PetSpecies.crow, "Kai")
        val crow = r.active!!.id
        r = RosterRules.awardXp(r, crow, 10)
        r = adopt(r, PetSpecies.whiteDove, "Luz")
        assertEquals(0, r.active!!.skills.level)
        assertEquals(100.0, r.active!!.status.energy)
        r = RosterRules.spendEnergy(r, crow, 4.0)
        assertEquals(96.0, r.byId(crow)!!.status.energy)
        val later = now + 2L * 24 * 3600 * 1000
        r = RosterRules.switchTo(r, crow, later)
        assertEquals(1, r.active!!.skills.level)
        assertEquals(later, r.active!!.status.lastUpdatedAt)
        assertEquals(80.0, r.active!!.status.satiety)
    }

    @Test fun repositoryRoundTrips() = runTest {
        val repo = LocalPetRepository(InMemoryKeyValueStore())
        assertNull(repo.load())
        val roster = adopt(PetRosterEntity.empty, PetSpecies.whiteDove, "Luz")
        repo.save(roster)
        assertEquals(roster, repo.load())
        assertNull(LocalPetRepository.decode("{not json"))
        assertNull(LocalPetRepository.decode("""{"v":99,"roster":{}}"""))
        assertTrue(LocalPetRepository.encode(roster).contains("\"status\""))
    }
}
