package com.petbattle.shared_features.pet_status.domain

/** Qué hizo la mascota con la comida que se le ofreció. */
enum class FeedResult {
    /** Era de su dieta y tenía hambre: le sacia y le sienta bien. */
    eaten,

    /** Era de su dieta pero no tenía hambre: le sacia, pero le sienta mal (baja la digestión). */
    overate,

    /** No es de su dieta: se la come a disgusto, no le sacia y le sienta mal. */
    disliked,

    /** Era un polluelo, se la comió y con esa comida ha crecido a su especie adulta. */
    grew,
}
