package space.kscience.controls.compose

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * A single 2D drawable
 */
@Immutable
public sealed interface DeviceDrawable2D {

    public fun DrawScope.draw()

    override fun equals(other: Any?): Boolean
}

@Immutable
public data class CircleDrawable2D(val position: Offset, val radius: Float, val color: Color) : DeviceDrawable2D {
    override fun DrawScope.draw() {
        drawCircle(color, radius = radius, center = position)
    }
}

@Drawable2DBuilder
public fun DeviceDrawable2DStore.circle(id: String, position: Offset, radius: Float, color: Color) {
    emit(id, CircleDrawable2D(position, radius, color))
}

@Immutable
public data class RectangleDrawable2D(
    val position: Offset,
    val rectangleSize: Size,
    val color: Color,
    val rotateDegrees: Float = 0f,
) : DeviceDrawable2D {
    override fun DrawScope.draw() {
        rotate(rotateDegrees) {
            drawRect(
                color = color,
                topLeft = Offset(
                    (position.x - rectangleSize.width / 2),
                    (position.y - rectangleSize.height / 2)
                ),
                size = Size(rectangleSize.width, rectangleSize.height)
            )
        }
    }
}

@Drawable2DBuilder
public fun DeviceDrawable2DStore.rectangle(
    id: String,
    position: Offset,
    rectangleSize: Size,
    color: Color,
    rotateDegrees: Float = 0f,
) {
    emit(id, RectangleDrawable2D(position, rectangleSize, color, rotateDegrees))
}

