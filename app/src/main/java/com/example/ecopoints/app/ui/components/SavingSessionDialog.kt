package com.example.ecopoints.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** Bloquea nuevas acciones mientras se confirma el último estado antes de cerrar la sesión. */
@Composable
fun SavingSessionDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Guardando cambios") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator()
                Text("Espera mientras se guardan tus cambios antes de cerrar sesión.")
            }
        },
        confirmButton = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
}
