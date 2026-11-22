package com.petbattle.shared_features

import com.petbattle.core.persistence.InMemoryKeyValueStore
import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.pet_food.domain.Food
import com.petbattle.shared_features.pet_status.domain.FeedResult
import com.petbattle.shared_features.pet_status.domain.GrowthRules
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import com.petbattle.shared_features.pet_species.domain.isChick
import com.petbattle.shared_features.pet_status.domain.PetStatusRules
import com.petbattle.shared_features.pet_status.domain.entities.PetStatusEntity
import com.petbattle.shared_features.pet_skills.domain.PetElement
import com.petbattle.shared_features.pet_skills.domain.PetProgression
import com.petbattle.shared_features.pet_skills.domain.PetSkillsCatalog
import com.petbattle.shared_features.pet_skills.domain.PetType
import com.petbattle.shared_features.pet_skills.domain.element
import com.petbattle.shared_features.pet_skills.domain.entities.BurnEffect
import com.petbattle.shared_features.pet_skills.domain.entities.PetSkillEntity
import com.petbattle.shared_features.pet_skills.domain.entities.PetSkillsEntity
import com.petbattle.shared_features.pet_skills.domain.type
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.domain.biomes
import com.petbattle.shared_features.pet_species.domain.diet
import com.petbattle.shared_features.pet_status.data.LocalPetRepository
import com.petbattle.shared_features.pet_status.domain.PetNameError
import com.petbattle.shared_features.pet_status.domain.RosterRules
import com.petbattle.shared_features.pet_status.domain.entities.PetRosterEntity
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RosterAndSkillsTest {
    private val now = 1_767_268_800_000L
    private val inWoodland = RosterRules.enterBiome(PetRosterEntity.empty, Biome.woodland, now)
    private fun hatch(r: PetRosterEntity, n: String) = RosterRules.hatch(r, n, now)

    /** Añade directamente un adulto de [s] (en el primer bioma de su especie) y lo activa. */
    private fun withAdult(r: PetRosterEntity, s: PetSpecies, n: String): PetRosterEntity {
        val pet = PetEntity.newborn(n, s, now, id = "$n-id")
        return r.copy(pets = r.pets + pet, activeId = pet.id, currentBiome = pet.biome)
    }

    /** Da de comer [food] [times] veces a la mascota activa (con hambre siempre). */
    private fun feed(r: PetRosterEntity, food: Food, times: Int, random: Random = Random(1)): Pair<PetRosterEntity, FeedResult?> {
        var roster = r
        var result: FeedResult? = null
        repeat(times) {
            roster = RosterRules.updateActiveStatus(roster) { it.copy(satiety = 0.0) }
            val (next, res) = RosterRules.feedActive(roster, food, random)
            roster = next
            result = res
        }
        return roster to result
    }

    @Test fun everySpeciesHasDietAndEveryFoodHasSpecies() {
        for (s in PetSpecies.entries) assertTrue(s.diet.isNotEmpty(), s.name)
        assertEquals(Food.entries.toSet(), PetSpecies.adults.flatMap { it.diet }.toSet())
    }

    @Test fun likedFoodIsEatenOtherFoodIsRejected() {
        val hungry = PetStatusEntity.initial(now).copy(satiety = 20.0)
        val diet = PetSpecies.whiteDove.diet
        val (fed, eaten) = PetStatusRules.offerFood(hungry, Food.seeds, diet)
        assertEquals(FeedResult.eaten, eaten)
        assertTrue(fed.satiety > hungry.satiety)
        val (same, rejected) = PetStatusRules.offerFood(hungry, Food.carrion, diet)
        assertEquals(FeedResult.rejected, rejected)
        assertEquals(hungry, same)
    }

    @Test fun everySpeciesHasBiomesAndEveryBiomeHasSpecies() {
        for (s in PetSpecies.entries) assertTrue(s.biomes.isNotEmpty(), s.name)
        val covered = PetSpecies.adults.flatMap { it.biomes }.toSet()
        assertEquals(Biome.entries.toSet(), covered)
    }

    @Test fun speciesTraits() {
        for (s in PetSpecies.entries) {
            val expected = when (s) {
                PetSpecies.crow -> PetType.darkness
                PetSpecies.whiteDove -> PetType.light
                else -> PetType.elemental
            }
            assertEquals(expected, s.type, s.name)
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

    @Test fun hatchingNeedsABiomeAndAValidUniqueName() {
        assertFalse(RosterRules.canHatch(PetRosterEntity.empty))
        assertFailsWith<IllegalArgumentException> { hatch(PetRosterEntity.empty, "Kai") }
        val roster = hatch(inWoodland, "  Kai  ")
        val kai = roster.active!!
        assertEquals("Kai", kai.name)
        assertTrue(kai.species.isChick)
        assertEquals(Biome.woodland, kai.biome)
        assertEquals(PetNameError.empty, RosterRules.validateName(roster, "  "))
        assertEquals(PetNameError.taken, RosterRules.validateName(roster, "kai"))
        assertEquals(PetNameError.tooLong, RosterRules.validateName(roster, "x".repeat(17)))
        assertNull(RosterRules.validateName(roster, "Luna"))
        assertFailsWith<IllegalArgumentException> { hatch(roster, "kai") }
    }

    @Test fun severalPetsShareABiomeAndEachBiomeKeepsItsOwn() {
        var r = hatch(inWoodland, "Kai")
        r = hatch(r, "Luz")
        assertEquals(listOf("Kai", "Luz"), r.biomePets.map { it.name })
        r = RosterRules.enterBiome(r, Biome.tundra, now)
        assertEquals(Biome.tundra, r.currentBiome)
        assertNull(r.active)
        assertTrue(r.biomePets.isEmpty())
        r = hatch(r, "Nieve")
        assertEquals(listOf("Nieve"), r.biomePets.map { it.name })
        r = RosterRules.enterBiome(r, Biome.woodland, now)
        assertEquals(listOf("Kai", "Luz"), r.biomePets.map { it.name })
        assertEquals("Kai", r.active!!.name)
    }

    @Test fun releasingRemovesThePetForGoodAndActivatesAnotherOfTheBiome() {
        var r = hatch(inWoodland, "Kai")
        val kai = r.active!!.id
        r = hatch(r, "Luz")
        val luz = r.active!!.id
        r = RosterRules.release(r, luz, now)
        assertNull(r.byId(luz))
        assertEquals(kai, r.activeId)
        assertNull(RosterRules.validateName(r, "Luz"))
        r = RosterRules.release(r, kai, now)
        assertTrue(r.pets.isEmpty())
        assertNull(r.active)
        assertEquals(Biome.woodland, r.currentBiome)
    }

    @Test fun chickEatsAnythingAndGrowsIntoABiomeSpeciesThatEatsItsFavourite() {
        val chick = hatch(inWoodland, "Kai")
        val (half, res) = feed(chick, Food.worms, GrowthRules.MEALS_TO_GROW - 1)
        assertEquals(FeedResult.eaten, res)
        assertTrue(half.active!!.species.isChick)
        assertEquals(GrowthRules.MEALS_TO_GROW - 1, half.active!!.meals[Food.worms])
        val (grown, last) = feed(half, Food.worms, 1)
        assertEquals(FeedResult.grew, last)
        val adult = grown.active!!
        assertFalse(adult.species.isChick)
        assertTrue(Biome.woodland in adult.biomes, adult.species.name)
        assertTrue(Food.worms in adult.diet, adult.species.name)
        assertEquals("Kai", adult.name)
        assertEquals(0, adult.skills.level)
        assertTrue(adult.meals.isEmpty())
    }

    @Test fun growthTiesAndImpossibleDietsPickAtRandomWithinTheBiome() {
        // Empate gusanos/néctar en el bosque: solo encajan quienes comen gusanos (no hay colibrí).
        val tie = mapOf(Food.worms to 5, Food.nectar to 5)
        val woodlandWorms = setOf(PetSpecies.crow, PetSpecies.redRobin, PetSpecies.woodThrush)
        val seen = (0 until 200).map { GrowthRules.adultFor(Biome.woodland, tie, Random(it)) }.toSet()
        assertEquals(woodlandWorms, seen)
        // Nadie en la costa come néctar: cualquiera de la costa.
        val coast = PetSpecies.adults.filter { Biome.coast in it.biomes }.toSet()
        val anyCoast = (0 until 200).map { GrowthRules.adultFor(Biome.coast, mapOf(Food.nectar to 10), Random(it)) }.toSet()
        assertEquals(coast, anyCoast)
    }

    @Test fun oldSavesWithoutBiomesAreMigrated() {
        val old = """{"v":1,"roster":{"pets":[{"id":"crow-1","name":"Kai","species":"crow",
            "status":{"satiety":80.0,"energy":100.0,"happiness":80.0,"hygiene":80.0,"lastUpdatedAt":1},
            "skills":{"element":"earth","type":"darkness"}}],"activeId":"crow-1"}}"""
        val roster = RosterRules.migrate(LocalPetRepository.decode(old)!!)
        assertEquals(PetSpecies.crow.biomes.first(), roster.active!!.biome)
        assertEquals(roster.active!!.biome, roster.currentBiome)
    }

    @Test fun eachPetKeepsItsOwnLevelAndStatus() {
        var r = withAdult(PetRosterEntity.empty, PetSpecies.crow, "Kai")
        val crow = r.active!!.id
        r = RosterRules.awardXp(r, crow, 10)
        r = withAdult(r, PetSpecies.whiteDove, "Luz")
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
        val roster = hatch(inWoodland, "Luz")
        repo.save(roster)
        assertEquals(roster, repo.load())
        assertNull(LocalPetRepository.decode("{not json"))
        assertNull(LocalPetRepository.decode("""{"v":99,"roster":{}}"""))
        assertTrue(LocalPetRepository.encode(roster).contains("\"status\""))
    }
}
