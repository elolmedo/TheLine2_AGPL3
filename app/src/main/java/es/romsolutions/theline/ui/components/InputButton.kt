package es.romsolutions.theline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun InputButton(
    icon: ImageVector,
    onPressChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Gray.copy(alpha = 0.3f),
    contentColor: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(70.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onPressChanged(true)
                    
                    // Wait for all pointers to be up
                    var anyPointerDown = true
                    while (anyPointerDown) {
                        val event = awaitPointerEvent()
                        anyPointerDown = event.changes.any { it.pressed }
                    }
                    onPressChanged(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(35.dp)
        )
    }
}
