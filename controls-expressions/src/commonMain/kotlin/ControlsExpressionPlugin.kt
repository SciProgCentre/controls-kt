package space.kscience.controls.expressions

import space.kscience.controls.constructor.ConstructorPlugin
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.ValueStateFactory
import space.kscience.controls.expressions.MathValueStateFactory.arg
import space.kscience.controls.expressions.MathValueStateFactory.windows
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.PluginFactory
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import space.kscience.kmath.expressions.Expression
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

public class ControlsExpressionPlugin : AbstractPlugin() {

    public val constructor: ConstructorPlugin by require(ConstructorPlugin)

    public val deviceManager: DeviceManager get() = constructor.deviceManager

    override val tag: PluginTag get() = Companion.tag


    public val unaryOperations: Map<String, (arg: ValueState<Double?>) -> ValueState<Double?>> = mapOf(
        "integrateMinute" to { argValue ->
            argValue.integrate(1.minutes, context)
        },

        "integrateHour" to { argValue ->
            argValue.integrate(1.hours, context)
        },

        "integrateDay" to { argValue ->
            argValue.integrate(1.days, context)
        },

        "diff" to { argValue ->
            argValue.differentiate(context)
        }
    )

    public val binaryOperations: Map<String, (arg1: ValueState<Double?>, arg2: ValueState<Double?>) -> ValueState<Double?>>
        get() = emptyMap()

    public val functions: Map<String, Expression<ValueState<Double?>>> = mapOf(
        "integrate" to Expression { args ->
            val argValue = args[arg] ?: error("Integrate argument is missing")
            val duration = args[windows]?.value?.seconds ?: error("Duration argument is missing")
            argValue.integrate(duration, context)
        }
    )


    override fun content(target: String): Map<Name, Any> = when (target) {
        ValueStateFactory.PROVIDER_TAGET -> mapOf(
            Name.of(ExpressionValueStateFactory.TYPE) to ExpressionValueStateFactory,
            Name.of(MathValueStateFactory.TYPE) to MathValueStateFactory
        )

        DeviceManager.DEVICE_FACTORY_TARGET -> mapOf(
            Name.of(ComputationDevice.TYPE) to ComputationDevice
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