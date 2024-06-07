package space.kscience.controls.compose

import androidx.compose.ui.Modifier

public inline fun Modifier.conditional(
    condition: Boolean,
    modifier: Modifier.() -> Modifier,
): Modifier = if (condition) {
    then(modifier(Modifier))
} else {
    this
}