package com.petbattle.shared_features.pet_status.domain

import com.petbattle.shared_features.pet_status.domain.entities.PetRosterEntity

/** Contrato de persistencia. Al dominio no le importa dónde se almacene. */
interface PetRepository {
    /** Devuelve `null` si aún no hay partida guardada (válida). */
    suspend fun load(): PetRosterEntity?
    suspend fun save(roster: PetRosterEntity)
    suspend fun clear()
}
