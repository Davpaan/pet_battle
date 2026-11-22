package com.petbattle.shared_features.pet_status.domain

/** Qué hizo la mascota con la comida que se le ofreció. */
enum class FeedResult {
    /** Le gustaba y se la comió. */
    eaten,

    /** No es de su dieta: la rechazó. */
    rejected,

    /** Era un polluelo, se la comió y con esa comida ha crecido a su especie adulta. */
    grew,
}
