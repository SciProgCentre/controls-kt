package space.kscience.controls.expressions

import space.kscience.controls.constructor.*
import space.kscience.controls.nullable
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.double
import space.kscience.kmath.expressions.Expression
import space.kscience.kmath.expressions.Symbol

public class ComputationDeviceAlarm(
    context: Context,
    public val argNames: Set<String>,
    public val expression: Expression<ValueState<Double?>>,
    meta: Meta = Meta.EMPTY
) : DeviceConstructor(context, meta), BoundStateHolder {

    private val args: Map<String, LateBindValueState<Double?>> = argNames.associateWith { LateBindValueState(null) }

    override fun bind(state: ValueState<Meta>, inputName: String) {
        args[inputName]?.bind(state.map { it.double }) ?: error("Input name $inputName not defined in $argNames")
    }

    public val result: ValueState<Double?> = expression(args.mapKeys { Symbol(it.key) })


    init {
        registerProperty(
            name = "result",
            converter = MetaConverter.double.nullable(),
            state = result
        )
    }

}