package com.petbattle.shared_features.pet_status.domain

import com.petbattle.shared_features.pet_status.domain.entities.PetStatusEntity

/**
 * High-level emotional state, derived (never stored) from the status.
 * Ordered by priority: the first matching mood wins.
 */
enum class PetMood { sleeping, hungry, tired, dirty, sad, content, happy }

/** Average of all stats, 0..1. */
val PetStatusEntity.wellbeing: Double
    get() = (satiety + energy + happiness + hygiene) / (4 * PetStatusBalance.MAX_STAT)

val PetStatusEntity.mood: PetMood
    get() = when {
        isSleeping -> PetMood.sleeping
        satiety < 25 -> PetMood.hungry
        energy < 20 -> PetMood.tired
        hygiene < 25 -> PetMood.dirty
        happiness < 35 -> PetMood.sad
        happiness >= 70 && wellbeing >= 0.65 -> PetMood.happy
        else -> PetMood.content
    }
