package com.petbattle.shared_features

import com.petbattle.core.persistence.InMemoryKeyValueStore
import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.pet_food.domain.Food
import com.petbattle.shared_features.pet_status.domain.FeedResult
import com.petbattle.shared_features.pet_status.domain.GrowthRules
import com.petbattle.shared_features.pet_status.domain.PetHiddenStats
import com.petbattle.shared_features.pet_status.domain.PetStatusBalance
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
import com.petbattle.shared_features.pet_status.domain.GameRules
import com.petbattle.shared_features.pet_status.domain.entities.GameStateEntity
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameStateAndSkillsTest {
    private val now = 1_767_268_800_000L
    /** Partida nueva con oro de sobra y el bosque comprado, situada en el bosque. */
    private val inWoodland = GameRules.enterBiome(
        GameRules.buyBiome(GameStateEntity.newGame.copy(gold = 1_000), Biome.woodland), Biome.woodland, now,
    )
    private fun hatch(r: GameStateEntity, n: String) = GameRules.hatch(r, n, now)

    /** Añade directamente un adulto de [s] (en el primer bioma de su especie) y lo activa. */
    private fun withAdult(r: GameStateEntity, s: PetSpecies, n: String): GameStateEntity {
        val pet = PetEntity.newborn(n, s, now, id = "$n-id")
        return r.copy(pets = r.pets + pet, activeId = pet.id, currentBiome = pet.biome)
    }

    /** Da de comer [food] [times] veces a la mascota activa (con hambre siempre). */
    private fun feed(r: GameStateEntity, food: Food, times: Int, random: Random = Random(1)): Pair<GameStateEntity, FeedResult?> {
        var gameState = r
        var result: FeedResult? = null
        repeat(times) {
            gameState = GameRules.updateActiveStatus(gameState) { it.copy(satiety = 0.0) }
            val (next, res) = GameRules.feedActive(gameState, food, random)
            gameState = next
            result = res
        }
        return gameState to result
    }

    @Test fun everySpeciesHasDietAndEveryFoodHasSpecies() {
        for (s in PetSpecies.entries) assertTrue(s.diet.isNotEmpty(), s.name)
        assertEquals(Food.entries.toSet(), PetSpecies.adults.flatMap { it.diet }.toSet())
    }

    @Test fun foodIsAlwaysEatenButOnlyItsOwnFoodWhenHungrySatiatesAndDigestsWell() {
        val diet = PetSpecies.whiteDove.diet
        val hungry = PetStatusEntity.initial(now).copy(satiety = 20.0, digestion = 50.0)
        // Su comida con hambre: sacia, mejora la digestión y gana confianza.
        val (good, eaten) = PetStatusRules.offerFood(hungry, Food.seeds, diet)
        assertEquals(FeedResult.eaten, eaten)
        assertTrue(good.satiety > hungry.satiety)
        assertTrue(good.digestion > hungry.digestion)
        assertTrue(good.trust > hungry.trust)
        // Comida que no es de su dieta: se la come, pero no sacia y le sienta mal.
        val (bad, disliked) = PetStatusRules.offerFood(hungry, Food.carrion, diet)
        assertEquals(FeedResult.disliked, disliked)
        assertEquals(hungry.satiety, bad.satiety)
        assertTrue(bad.digestion < hungry.digestion)
        // Su comida sin hambre: sacia, pero le sienta mal.
        val full = hungry.copy(satiety = 90.0)
        val (over, overate) = PetStatusRules.offerFood(full, Food.seeds, diet)
        assertEquals(FeedResult.overate, overate)
        assertTrue(over.digestion < full.digestion)
        assertEquals(full.trust, over.trust)
        // Lo peor: comida ajena y sin hambre.
        val (worst, _) = PetStatusRules.offerFood(full, Food.carrion, diet)
        assertTrue(worst.digestion < bad.digestion)
        // Dormida no come.
        val asleep = hungry.copy(isSleeping = true)
        assertEquals(asleep to null, PetStatusRules.offerFood(asleep, Food.seeds, diet))
    }

    @Test fun digestionAlwaysRecoversOnItsOwn() {
        val upset = PetStatusEntity.initial(now).copy(digestion = 20.0)
        val oneHour = PetStatusRules.advanceTo(upset, now + 3_600_000L)
        assertEquals(20.0 + PetStatusBalance.DIGESTION_RECOVERY_PER_HOUR, oneHour.digestion, 1e-6)
        val muchLater = PetStatusRules.advanceTo(upset, now + 24 * 3_600_000L)
        assertEquals(PetStatusBalance.MAX_STAT, muchLater.digestion)
    }

    @Test fun trustStartsLowGrowsSlowlyWithCareAndDropsWithNeglect() {
        val pet = PetStatusEntity.initial(now)
        assertEquals(PetStatusBalance.INITIAL_TRUST, pet.trust)
        // Atender una necesidad pendiente sube la confianza; si no hacía falta, no.
        val dirty = pet.copy(hygiene = 40.0)
        assertEquals(pet.trust + PetStatusBalance.TRUST_PER_CARE, PetStatusRules.clean(dirty).trust)
        assertEquals(pet.trust, PetStatusRules.clean(pet.copy(hygiene = 90.0)).trust)
        val tired = pet.copy(energy = 30.0)
        assertEquals(pet.trust + PetStatusBalance.TRUST_PER_CARE, PetStatusRules.toggleSleep(tired).trust)
        // Descuidada (hambrienta y sucia) durante horas pierde confianza.
        val neglected = pet.copy(satiety = 5.0, hygiene = 5.0)
        val later = PetStatusRules.advanceTo(neglected, now + 3_600_000L)
        assertTrue(later.trust < neglected.trust)
    }

    @Test fun beakAndClawsRustWithoutFightingExceptChicksAndSharpenWhenFiring() {
        val pet = PetStatusEntity.initial(now).copy(beakAndClaws = 50.0)
        val hourLater = now + 3_600_000L
        assertTrue(PetStatusRules.advanceTo(pet, hourLater).beakAndClaws < 50.0)
        assertEquals(50.0, PetStatusRules.advanceTo(pet, hourLater, beakDecays = false).beakAndClaws)
        val fired = PetStatusRules.fire(pet)
        assertEquals(50.0 + PetStatusBalance.BEAK_PER_SHOT, fired.beakAndClaws)
        assertEquals(pet.energy - PetStatusBalance.ENERGY_PER_SHOT, fired.energy)
    }

    @Test fun socializationDependsOnWhoLivesInTheBiome() {
        var g = hatch(inWoodland, "Kai")
        val kai = g.active!!.id
        assertEquals(0.0, PetHiddenStats.socialization(g.byId(kai)!!, g)) // sola
        val crow = PetEntity.newborn("Cuervo", PetSpecies.crow, now, id = "crow", biome = Biome.woodland)
        g = g.copy(pets = g.pets + crow)
        assertEquals(50.0, PetHiddenStats.socialization(g.byId(kai)!!, g)) // otra especie
        g = hatch(g, "Luz") // otro polluelo
        assertEquals(100.0, PetHiddenStats.socialization(g.byId(kai)!!, g)) // misma especie
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
        assertFalse(GameRules.canHatch(GameStateEntity.empty))
        assertFailsWith<IllegalArgumentException> { hatch(GameStateEntity.empty, "Kai") }
        val gameState = hatch(inWoodland, "  Kai  ")
        val kai = gameState.active!!
        assertEquals("Kai", kai.name)
        assertTrue(kai.species.isChick)
        assertEquals(Biome.woodland, kai.biome)
        assertEquals(PetNameError.empty, GameRules.validateName(gameState, "  "))
        assertEquals(PetNameError.taken, GameRules.validateName(gameState, "kai"))
        assertEquals(PetNameError.tooLong, GameRules.validateName(gameState, "x".repeat(17)))
        assertNull(GameRules.validateName(gameState, "Luna"))
        assertFailsWith<IllegalArgumentException> { hatch(gameState, "kai") }
    }

    @Test fun severalPetsShareABiomeAndEachBiomeKeepsItsOwn() {
        var r = hatch(inWoodland, "Kai")
        r = hatch(r, "Luz")
        assertEquals(listOf("Kai", "Luz"), r.biomePets.map { it.name })
        r = GameRules.buyBiome(r, Biome.tundra)
        r = GameRules.enterBiome(r, Biome.tundra, now)
        assertEquals(Biome.tundra, r.currentBiome)
        assertNull(r.active)
        assertTrue(r.biomePets.isEmpty())
        r = hatch(r, "Nieve")
        assertEquals(listOf("Nieve"), r.biomePets.map { it.name })
        r = GameRules.enterBiome(r, Biome.woodland, now)
        assertEquals(listOf("Kai", "Luz"), r.biomePets.map { it.name })
        assertEquals("Kai", r.active!!.name)
    }

    @Test fun newGameStartsInTheFreeGrasslandWithAFreeEgg() {
        val r = GameStateEntity.newGame
        assertEquals(Biome.grassland, r.currentBiome)
        assertEquals(listOf(Biome.grassland), r.unlockedBiomes)
        assertEquals(0, r.gold)
        assertEquals(0, GameRules.eggPrice(r))
        assertTrue(GameRules.canHatch(r))
        val first = hatch(r, "Kai")
        assertEquals(0, first.gold)
        assertEquals(GameRules.EGG_PRICE, GameRules.eggPrice(first))
        assertFalse(GameRules.canHatch(first))
        assertEquals(GameRules.EGG_PRICE, GameRules.goldMissingForEgg(first))
        assertFailsWith<IllegalArgumentException> { hatch(first, "Luz") }
    }

    @Test fun eggsAndBiomesCostGoldAndLockedBiomesCannotBeVisited() {
        var r = hatch(GameStateEntity.newGame, "Kai")
        // Bloqueado: no se puede ir ni comprar sin oro.
        assertEquals(r, GameRules.enterBiome(r, Biome.coast, now))
        assertEquals(r, GameRules.buyBiome(r, Biome.coast))
        r = GameRules.awardGold(r, 25) // 25 enemigos
        r = hatch(r, "Luz")
        assertEquals(15, r.gold)
        assertFalse(GameRules.canBuyBiome(r, Biome.coast))
        r = GameRules.awardGold(r, 5)
        r = GameRules.buyBiome(r, Biome.coast)
        assertEquals(0, r.gold)
        assertTrue(r.isUnlocked(Biome.coast))
        assertEquals(r, GameRules.buyBiome(r, Biome.coast)) // no se compra dos veces
        r = GameRules.enterBiome(r, Biome.coast, now)
        assertEquals(Biome.coast, r.currentBiome)
    }

    @Test fun releasingRemovesThePetForGoodAndActivatesAnotherOfTheBiome() {
        var r = hatch(inWoodland, "Kai")
        val kai = r.active!!.id
        r = hatch(r, "Luz")
        val luz = r.active!!.id
        r = GameRules.release(r, luz, now)
        assertNull(r.byId(luz))
        assertEquals(kai, r.activeId)
        assertNull(GameRules.validateName(r, "Luz"))
        r = GameRules.release(r, kai, now)
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
        val woodlandWorms = setOf(PetSpecies.crow, PetSpecies.redRobin, PetSpecies.woodThrush, PetSpecies.chucao)
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
        val gameState = GameRules.migrate(LocalPetRepository.decode(old)!!)
        assertEquals(PetSpecies.crow.biomes.first(), gameState.active!!.biome)
        assertEquals(gameState.active!!.biome, gameState.currentBiome)
        // Conserva sus biomas, la pradera es gratis y ya no le toca huevo gratis.
        assertTrue(gameState.isUnlocked(gameState.active!!.biome))
        assertTrue(gameState.isUnlocked(Biome.grassland))
        assertTrue(gameState.freeEggUsed)
    }

    @Test fun eachPetKeepsItsOwnLevelAndStatus() {
        var r = withAdult(GameStateEntity.empty, PetSpecies.crow, "Kai")
        val crow = r.active!!.id
        r = GameRules.awardXp(r, crow, 10)
        r = withAdult(r, PetSpecies.whiteDove, "Luz")
        assertEquals(0, r.active!!.skills.level)
        assertEquals(100.0, r.active!!.status.energy)
        r = GameRules.spendEnergy(r, crow, 4.0)
        assertEquals(96.0, r.byId(crow)!!.status.energy)
        val later = now + 2L * 24 * 3600 * 1000
        r = GameRules.switchTo(r, crow, later)
        assertEquals(1, r.active!!.skills.level)
        assertEquals(later, r.active!!.status.lastUpdatedAt)
        assertEquals(80.0, r.active!!.status.satiety)
    }

    @Test fun repositoryRoundTrips() = runTest {
        val repo = LocalPetRepository(InMemoryKeyValueStore())
        assertNull(repo.load())
        val gameState = hatch(inWoodland, "Luz")
        repo.save(gameState)
        assertEquals(gameState, repo.load())
        assertNull(LocalPetRepository.decode("{not json"))
        assertNull(LocalPetRepository.decode("""{"v":99,"roster":{}}"""))
        assertTrue(LocalPetRepository.encode(gameState).contains("\"status\""))
    }
}
