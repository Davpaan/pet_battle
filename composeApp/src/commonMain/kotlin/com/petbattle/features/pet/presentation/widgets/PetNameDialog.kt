package com.petbattle.features.pet.presentation.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.petbattle.shared_features.pet_skills.domain.element
import com.petbattle.shared_features.pet_skills.domain.type
import com.petbattle.shared_features.pet_skills.presentation.ElementChip
import com.petbattle.shared_features.pet_skills.presentation.TypeChip
import com.petbattle.shared_features.pet_species.domain.PetSpecies
import com.petbattle.shared_features.pet_species.presentation.label
import com.petbattle.shared_features.pet_species.presentation.widgets.BirdFrame
import com.petbattle.shared_features.pet_status.domain.PetNameError
import com.petbattle.shared_features.pet_status.domain.RosterRules

/**
 * Asks for the name of a new pet. A pet can't be saved without one, so the
 * dialog only closes with a valid name (or when cancelled). [submit] tries to
 * adopt and returns why it failed, or `null` on success.
 */
@Composable
fun PetNameDialog(species: PetSpecies, submit: (String) -> PetNameError?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<PetNameError?>(null) }
    fun trySubmit() {
        val e = submit(name)
        if (e == null) onDismiss() else error = e
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo ${species.label.lowercase()}") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BirdFrame(species, 96.dp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ElementChip(species.element)
                    TypeChip(species.type)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= RosterRules.MAX_NAME_LENGTH) name = it
                        error = null
                    },
                    label = { Text("Nombre") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { Text(error?.message ?: "${name.length}/${RosterRules.MAX_NAME_LENGTH}") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { trySubmit() }),
                )
                Text("Empezará en el nivel 0.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = ::trySubmit) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

val PetNameError.message get() = when (this) {
    PetNameError.empty -> "Ponle un nombre para poder guardarla"
    PetNameError.tooLong -> "Máximo ${RosterRules.MAX_NAME_LENGTH} caracteres"
    PetNameError.taken -> "Ya tienes una mascota con ese nombre"
}
