package com.petbattle.shared_features.pet_status.domain

import com.petbattle.shared_features.pet_status.domain.entities.PetNeedsEntity

/**
 * Bienestar general (0..1): la media de hambre, descanso, diversión e higiene. No es un stat
 * del juego: solo se usa para desaturar en pantalla al pájaro que está mal.
 */
val PetNeedsEntity.wellbeing: Double
    get() = (satiety + energy + happiness + hygiene) / (4 * PetNeedsBalance.MAX_STAT)
