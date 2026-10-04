package space.kscience.controls.expressions

import kotlinx.coroutines.CoroutineScope
import space.kscience.controls.constructor.ConstructorPlugin
import space.kscience.controls.constructor.DeviceValueStateFactory
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.ValueStateFactory
import space.kscience.controls.constructor.expressions.ExpressionValueStateFactory
import space.kscience.controls.constructor.expressions.integrate
import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.kmath.expressions.Expression
import space.kscience.kmath.expressions.Symbol
import space.kscience.kmath.expressions.symbol
import kotlin.time.Duration.Companion.seconds

public class ControlsExpressionPlugin : AbstractPlugin() {

    public val constructor by require(ConstructorPlugin)

    override val tag: PluginTag
        get() = Companion.tag

    override fun content(target: String): Map<Name, Any> = when (target) {
        ValueStateFactory.PROVIDER_TAGET -> mapOf(
            Name.of(ExpressionValueStateFactory.TYPE) to ExpressionValueStateFactory,
            Name.of(MathValueStateFactory.TYPE) to MathValueStateFactory
        )

        else -> super.content(target)
    }

    public companion object : PluginFactory<ControlsExpressionPlugin> {

        override val tag: PluginTag = PluginTag("controls-expression")
        override fun build(
            context: Context,
            meta: Meta
        ): ControlsExpressionPlugin = ControlsExpressionPlugin()

    }
}