package com.tvflow.app.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun ParentalPinDialog(
    categoryName: String,
    error: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember(categoryName) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catégorie verrouillée") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("Saisis le PIN parental pour ouvrir $categoryName.")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                    label = { Text("PIN") },
                    singleLine = true
                )
                if (!error.isNullOrBlank()) Text(error)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(pin) }, enabled = pin.length >= 4) { Text("Déverrouiller") } },
        dismissButton = { Button(onClick = onDismiss) { Text("Annuler") } }
    )
}
