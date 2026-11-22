package com.petbattle.shared_features

import com.petbattle.core.persistence.InMemoryKeyValueStore
import com.petbattle.core.time.DayCycle
import com.petbattle.shared_features.pet_combat.domain.PetCombatStatsRules
import com.petbattle.core.time.DayPhase
import com.petbattle.shared_features.biomes.domain.Biome
import com.petbattle.shared_features.pet_food.domain.Food
import com.petbattle.shared_features.pet_status.domain.FeedResult
import com.petbattle.shared_features.pet_status.domain.GrowthRules
import com.petbattle.shared_features.pet_status.domain.PetDerivedNeeds
import com.petbattle.shared_features.pet_status.domain.PetNeedsBalance
import com.petbattle.shared_features.pet_status.domain.entities.PetEntity
import com.petbattle.shared_features.pet_species.domain.isChick
import com.petbattle.shared_features.pet_status.domain.PetNeedsRules
import com.petbattle.shared_features.pet_status.domain.entities.PetNeedsEntity
import com.petbattle.shared_features.pet_skills.domain.PetElement
import com.petbattle.shared_features.pet_skills.domain.PetProgression
import com.petbattle.shared_features.pet_skills.domain.PetSkillsCatalog
import com.petbattle.shared_features.pet_skills.domain.PetType
import com.petbattle.shared_features.pet_skills.domain.element
import com.petbattle.shared_features.pet_skills.domain.entities.BurnEffect
import com.petbattle.shared_features.pet_skills.domain.entities.PetSkillEntity
import com.petbattle.shared_features.pet_skills.domain.entities.PetProgressionEntity
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
        GameRules.buyBiome(GameStateEntity.newGame.copy(gold = 1_000), Biome.woodland), Biome.woodland,
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
            gameState = GameRules.updateActiveNeeds(gameState) { it.copy(satiety = 0.0) }
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
        val hungry = PetNeedsEntity.initial(now).copy(satiety = 20.0, digestion = 50.0)
        // Su comida con hambre: sacia y mejora la digestión.
        val (good, eaten) = PetNeedsRules.offerFood(hungry, Food.seeds, diet)
        assertEquals(FeedResult.eaten, eaten)
        assertTrue(good.satiety > hungry.satiety)
        assertTrue(good.digestion > hungry.digestion)
        // Comida que no es de su dieta: se la come, pero no sacia y le sienta mal.
        val (bad, disliked) = PetNeedsRules.offerFood(hungry, Food.carrion, diet)
        assertEquals(FeedResult.disliked, disliked)
        assertEquals(hungry.satiety, bad.satiety)
        assertTrue(bad.digestion < hungry.digestion)
        // Su comida sin hambre: sacia, pero le sienta mal.
        val full = hungry.copy(satiety = 90.0)
        val (over, overate) = PetNeedsRules.offerFood(full, Food.seeds, diet)
        assertEquals(FeedResult.overate, overate)
        assertTrue(over.digestion < full.digestion)
        // Lo peor: comida ajena y sin hambre.
        val (worst, _) = PetNeedsRules.offerFood(full, Food.carrion, diet)
        assertTrue(worst.digestion < bad.digestion)
        // Dormida no come.
        val asleep = hungry.copy(isSleeping = true)
        assertEquals(asleep to null, PetNeedsRules.offerFood(asleep, Food.seeds, diet))
    }

    @Test fun theDayHasMorningEveningAndNightByLocalTime() {
        val hour = 3_600_000L
        val midnightUtc = 1_767_225_600_000L // 2026-01-01T00:00Z
        assertEquals(0, DayCycle.hourOf(midnightUtc, 0))
        assertEquals(1, DayCycle.hourOf(midnightUtc, 1 * 3_600_000)) // UTC+1
        assertEquals(23, DayCycle.hourOf(midnightUtc, -1 * 3_600_000)) // UTC-1
        assertEquals(14, DayCycle.hourOf(midnightUtc + 14 * hour, 0))
        assertEquals(DayPhase.night, DayCycle.phaseOfHour(0))
        assertEquals(DayPhase.night, DayCycle.phaseOfHour(5))
        assertEquals(DayPhase.morning, DayCycle.phaseOfHour(6))
        assertEquals(DayPhase.morning, DayCycle.phaseOfHour(17))
        assertEquals(DayPhase.evening, DayCycle.phaseOfHour(18))
        assertEquals(DayPhase.evening, DayCycle.phaseOfHour(23))
    }

    @Test fun atNightPetsRestWithoutFallingAsleep() {
        val tired = PetNeedsEntity.initial(now).copy(energy = 10.0)
        val later = now + 60_000L
        val day = PetNeedsRules.advanceTo(tired, later, isNight = { false })
        val night = PetNeedsRules.advanceTo(tired, later, isNight = { true })
        assertTrue(day.energy < 10.0) // de día el descanso no se recupera: baja poco a poco
        assertTrue(night.energy > 10.0) // de noche descansa...
        assertFalse(night.isSleeping) // ...pero sigue despierta: se puede jugar con ella
        assertTrue(PetNeedsRules.canFeed(night))
        // Y todo baja más despacio que de día.
        assertTrue(night.satiety > day.satiety)
    }

    @Test fun offlineCatchUpKnowsWhichHoursWereNight() {
        val hour = 3_600_000L
        val tired = PetNeedsEntity.initial(0).copy(energy = 0.0)
        // 2 horas fuera, solo la segunda de noche.
        val nightFrom = hour
        val result = PetNeedsRules.advanceTo(tired, 2 * hour, isNight = { it >= nightFrom })
        assertEquals(PetNeedsBalance.MAX_STAT, result.energy) // la hora de noche basta para descansar
        val allDay = PetNeedsRules.advanceTo(tired, 2 * hour, isNight = { false })
        assertEquals(0.0, allDay.energy)
    }

    @Test fun everyStoredNeedDecaysOverTimeAtItsOwnPace() {
        val full = PetNeedsEntity(
            satiety = 100.0, energy = 100.0, happiness = 100.0, hygiene = 100.0,
            digestion = 100.0, beakAndClaws = 100.0, plumage = 100.0, lastUpdatedAt = now,
        )
        val n = PetNeedsRules.advanceTo(full, now + 3_600_000L)
        assertEquals(100 - PetNeedsBalance.SATIETY_DECAY_PER_HOUR, n.satiety, 1e-6)
        assertEquals(100 - PetNeedsBalance.ENERGY_DECAY_PER_HOUR, n.energy, 1e-6)
        assertEquals(100 - PetNeedsBalance.HAPPINESS_DECAY_PER_HOUR, n.happiness, 1e-6)
        assertEquals(100 - PetNeedsBalance.HYGIENE_DECAY_PER_HOUR, n.hygiene, 1e-6)
        assertEquals(100 - PetNeedsBalance.DIGESTION_DECAY_PER_HOUR, n.digestion, 1e-6)
        assertEquals(100 - PetNeedsBalance.BEAK_DECAY_PER_HOUR, n.beakAndClaws, 1e-6)
        assertEquals(100 - PetNeedsBalance.PLUMAGE_DECAY_PER_HOUR, n.plumage, 1e-6)
    }

    @Test fun combatStatsAreTheirTotalScaledByThreeNeedsEach() {
        val base = PetEntity.newborn("A", PetSpecies.crow, now)
        fun pet(level: Int = 2, needs: PetNeedsEntity.() -> PetNeedsEntity = { this }) =
            base.copy(
                progression = base.progression.copy(level = level),
                needs = base.needs.copy(satiety = 100.0, energy = 100.0, happiness = 100.0, hygiene = 100.0).needs(),
            )
        // Todo al máximo (y social al máximo): rinde el total.
        val full = PetCombatStatsRules.statsFor(pet(), socialization = 100.0)
        assertEquals(2.4, full.maxDamage, 1e-9)
        assertEquals(full.maxDamage, full.damage, 1e-9)
        assertEquals(full.maxAttacksPerSecond, full.attacksPerSecond, 1e-9)
        assertEquals(100.0, full.confidence, 1e-9)
        // Daño: hambre, plumaje (100) y pico y garras.
        val hungry = PetCombatStatsRules.statsFor(pet { copy(satiety = 40.0, beakAndClaws = 60.0) }, 100.0)
        assertEquals(2.4 * (40 + 100 + 60) / 300, hungry.damage, 1e-9)
        // Velocidad: descanso, digestión y clima (100).
        val slow = PetCombatStatsRules.statsFor(pet { copy(energy = 50.0, digestion = 20.0) }, 100.0)
        assertEquals(full.maxAttacksPerSecond * (50 + 20 + 100) / 300, slow.attacksPerSecond, 1e-9)
        // Sin energía está agotada aunque digestión y clima estén bien.
        val spent = PetCombatStatsRules.statsFor(pet { copy(energy = 0.0) }, 100.0)
        assertTrue(spent.exhausted)
        assertNull(spent.fireInterval)
        // Confianza: diversión, social e higiene; es la probabilidad de acertar.
        val lonely = PetCombatStatsRules.statsFor(pet { copy(happiness = 70.0, hygiene = 80.0) }, socialization = 0.0)
        assertEquals((70.0 + 0 + 80) / 3, lonely.confidence, 1e-9)
        assertEquals(lonely.confidence / 100, lonely.hitChance, 1e-9)
    }

    @Test fun combatCleaningAndFoodMoveTheirOwnNeeds() {
        val pet = PetNeedsEntity.initial(now).copy(hygiene = 50.0, plumage = 50.0, beakAndClaws = 50.0)
        // Combate: gasta descanso, ensucia, despeina y afila.
        val fired = PetNeedsRules.fire(pet)
        assertEquals(pet.energy - PetNeedsBalance.ENERGY_PER_SHOT, fired.energy)
        assertEquals(50.0 - PetNeedsBalance.HYGIENE_PER_SHOT, fired.hygiene)
        assertEquals(50.0 - PetNeedsBalance.PLUMAGE_PER_SHOT, fired.plumage)
        assertEquals(50.0 + PetNeedsBalance.BEAK_PER_SHOT, fired.beakAndClaws)
        // Limpieza: mejora toda la salud.
        val cleaned = PetNeedsRules.clean(pet)
        assertEquals(50.0 + PetNeedsBalance.CLEAN_HYGIENE, cleaned.hygiene)
        assertEquals(50.0 + PetNeedsBalance.CLEAN_PLUMAGE, cleaned.plumage)
        assertEquals(50.0 + PetNeedsBalance.CLEAN_BEAK, cleaned.beakAndClaws)
        // Se puede limpiar aunque solo le falte plumaje.
        assertTrue(PetNeedsRules.canClean(pet.copy(hygiene = 100.0, beakAndClaws = 100.0)))
        // Comer no ensucia.
        val hungry = pet.copy(satiety = 20.0)
        assertEquals(hungry.hygiene, PetNeedsRules.offerFood(hungry, Food.seeds, PetSpecies.whiteDove.diet).first.hygiene)
        assertEquals(hungry.hygiene, PetNeedsRules.offerFood(hungry, Food.carrion, PetSpecies.whiteDove.diet).first.hygiene)
    }

    @Test fun socializationDependsOnWhoLivesInTheBiome() {
        var g = hatch(inWoodland, "Kai")
        val kai = g.active!!.id
        assertEquals(0.0, PetDerivedNeeds.socialization(g.byId(kai)!!, g)) // sola
        val crow = PetEntity.newborn("Cuervo", PetSpecies.crow, now, id = "crow", biome = Biome.woodland)
        g = g.copy(pets = g.pets + crow)
        assertEquals(50.0, PetDerivedNeeds.socialization(g.byId(kai)!!, g)) // otra especie
        g = hatch(g, "Luz") // otro polluelo
        assertEquals(100.0, PetDerivedNeeds.socialization(g.byId(kai)!!, g)) // misma especie
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
        var s = PetProgressionEntity.forSpecies(PetSpecies.cardinal)
        var kills = 0
        while (!PetProgression.isMaxLevel(s)) {
            s = PetProgression.gainXp(s, PetProgression.XP_PER_ENEMY); kills++
        }
        assertEquals(550, kills)
        assertEquals(s, PetProgression.gainXp(s, 5))
        val one = PetProgression.gainXp(PetProgressionEntity.forSpecies(PetSpecies.crow), 9)
        assertEquals(0 to 9, one.level to one.xp)
        val two = PetProgression.gainXp(one, 1)
        assertEquals(1 to 0, two.level to two.xp)
    }

    @Test fun skillsCatalog() {
        val sk = { s: PetSpecies -> PetProgressionEntity.forSpecies(s) }
        assertEquals(PetSkillsCatalog.fireBurn, PetSkillsCatalog.basicAttackFor(sk(PetSpecies.cardinal)))
        assertEquals(PetSkillsCatalog.waterSlow, PetSkillsCatalog.basicAttackFor(sk(PetSpecies.woodThrush)))
        assertEquals(PetSkillsCatalog.airGust, PetSkillsCatalog.basicAttackFor(sk(PetSpecies.hummingbird)))
        assertEquals(PetSkillsCatalog.earthRock, PetSkillsCatalog.basicAttackFor(sk(PetSpecies.crow)))
        assertEquals(BurnEffect(1.2, 3.0), PetSkillsCatalog.fireBurn.effect)

        val shadow = PetSkillEntity("shadow", "Sombra", "", 0)
        val crowOnly = PetSkillEntity("murder", "Bandada", "", 5)
        fun unlocked(s: PetSpecies, k: PetProgressionEntity) = PetSkillsCatalog.unlockedFor(
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
        r = GameRules.enterBiome(r, Biome.tundra)
        assertEquals(Biome.tundra, r.currentBiome)
        assertNull(r.active)
        assertTrue(r.biomePets.isEmpty())
        r = hatch(r, "Nieve")
        assertEquals(listOf("Nieve"), r.biomePets.map { it.name })
        r = GameRules.enterBiome(r, Biome.woodland)
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
        assertEquals(r, GameRules.enterBiome(r, Biome.coast))
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
        r = GameRules.enterBiome(r, Biome.coast)
        assertEquals(Biome.coast, r.currentBiome)
    }

    @Test fun releasingRemovesThePetForGoodAndActivatesAnotherOfTheBiome() {
        var r = hatch(inWoodland, "Kai")
        val kai = r.active!!.id
        r = hatch(r, "Luz")
        val luz = r.active!!.id
        r = GameRules.release(r, luz)
        assertNull(r.byId(luz))
        assertEquals(kai, r.activeId)
        assertNull(GameRules.validateName(r, "Luz"))
        r = GameRules.release(r, kai)
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
        assertEquals(0, adult.progression.level)
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
        assertEquals(0, r.active!!.progression.level)
        assertEquals(100.0, r.active!!.needs.energy)
        r = GameRules.spendEnergy(r, crow, 4.0)
        assertEquals(96.0, r.byId(crow)!!.needs.energy)
        r = GameRules.switchTo(r, crow)
        assertEquals(1, r.active!!.progression.level)
        assertEquals(96.0, r.active!!.needs.energy)
    }

    @Test fun timeIsAbsoluteAndCommonForEveryPet() {
        var r = withAdult(GameStateEntity.empty, PetSpecies.crow, "Kai")
        val kai = r.active!!.id
        r = withAdult(r, PetSpecies.whiteDove, "Luz") // Kai deja de estar seleccionada
        val later = now + 2 * 3_600_000L
        r = GameRules.advanceAll(r, later)
        // Las dos han vivido las mismas 2 horas, seleccionadas o no.
        for (pet in r.pets) {
            assertEquals(later, pet.needs.lastUpdatedAt)
            assertEquals(80.0 - 2 * PetNeedsBalance.SATIETY_DECAY_PER_HOUR, pet.needs.satiety, 1e-6)
        }
        // Volver a seleccionarla no le "devuelve" el tiempo.
        val before = r.byId(kai)!!.needs
        r = GameRules.switchTo(r, kai)
        assertEquals(before, r.active!!.needs)
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
