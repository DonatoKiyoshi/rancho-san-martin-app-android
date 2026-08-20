package com.rancho.gestion.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.rancho.gestion.data.entities.Animal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutocompleteAreteTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    animalesDisponibles: List<Animal>,
    modifier: Modifier = Modifier,
    onAnimalSelected: (Animal) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val filteredOptions = remember(value, animalesDisponibles) {
        if (value.isBlank()) animalesDisponibles
        else animalesDisponibles.filter { it.numeroArete.contains(value, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
        )

        if (filteredOptions.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                filteredOptions.take(10).forEach { animal ->
                    DropdownMenuItem(
                        text = {
                            Text("${animal.numeroArete} (${animal.color}, ${animal.pesoEntrada.toInt()}kg)")
                        },
                        onClick = {
                            onValueChange(animal.numeroArete)
                            onAnimalSelected(animal)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
