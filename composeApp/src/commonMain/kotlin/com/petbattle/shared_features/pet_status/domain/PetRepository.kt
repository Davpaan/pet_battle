package com.petbattle.shared_features.pet_status.domain

import com.petbattle.shared_features.pet_status.domain.entities.PetRosterEntity

/** Persistence contract. The domain doesn't care where it's stored. */
interface PetRepository {
    /** Returns `null` when there is no (valid) save yet. */
    suspend fun load(): PetRosterEntity?
    suspend fun save(roster: PetRosterEntity)
    suspend fun clear()
}
