package com.example.ecopoints.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.ecopoints.app.ui.theme.EcoGreen
import com.example.ecopoints.app.ui.theme.EcoGreenDark
import com.example.ecopoints.app.ui.theme.EcoSpacing

/** Todas las acciones comparten altura y área mínima. El contenedor decide su ancho disponible. */
private fun Modifier.actionSize() = heightIn(min = EcoSpacing.Button)
    .defaultMinSize(minWidth = EcoSpacing.ButtonMinWidth)

@Composable
fun EcoActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    colors: ButtonColors = ButtonDefaults.buttonColors(containerColor = EcoGreen),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    Button(onClick = onClick, modifier = modifier.actionSize(), enabled = enabled,
        shape = shape, colors = colors, contentPadding = contentPadding, content = content)
}

@Composable
fun EcoOutlinedActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = EcoGreenDark),
    border: BorderStroke? = BorderStroke(1.dp, EcoGreen),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(onClick = onClick, modifier = modifier.actionSize(), enabled = enabled,
        shape = shape, colors = colors, border = border, contentPadding = contentPadding, content = content)
}

@Composable
fun EcoTextActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(contentColor = EcoGreenDark),
    content: @Composable RowScope.() -> Unit
) {
    TextButton(onClick = onClick, modifier = modifier.actionSize(), enabled = enabled,
        colors = colors, content = content)
}

@Composable
fun EcoIconActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    IconButton(onClick = onClick, modifier = modifier.size(EcoSpacing.IconButton), enabled = enabled, content = content)
}

/** Confirmar y cancelar usan el mismo ancho en todos los diálogos. */
@Composable
fun EcoDialogButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textOnly: Boolean = false,
    colors: ButtonColors = if (textOnly) ButtonDefaults.textButtonColors(contentColor = EcoGreenDark)
        else ButtonDefaults.buttonColors(containerColor = EcoGreen),
    content: @Composable RowScope.() -> Unit
) {
    if (textOnly) EcoTextActionButton(onClick, modifier.width(EcoSpacing.DialogButtonWidth), enabled, colors, content)
    else EcoActionButton(onClick = onClick, modifier = modifier.width(EcoSpacing.DialogButtonWidth), enabled = enabled,
        colors = colors, content = content)
}
