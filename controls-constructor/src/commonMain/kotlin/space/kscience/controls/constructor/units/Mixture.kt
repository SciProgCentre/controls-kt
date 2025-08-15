package space.kscience.controls.constructor.units

import kotlin.jvm.JvmInline

@JvmInline
public value class MixtureComponent(public val name: String)

@JvmInline
public value class Mixture<U : UnitsOfMeasurement, T : Amount<U>>(
    public val components: Map<MixtureComponent, T>,
) : Amount<U> {
    override val value: Double get() = components.values.sumOf { it.value }

    public companion object{
        public fun <U : UnitsOfMeasurement, T : Amount<U>> ofAmounts(vararg entries: Pair<MixtureComponent, T>): Mixture<U, T> =
            Mixture(entries.toMap())

        public fun <U : UnitsOfMeasurement> ofFractions(vararg entries: Pair<MixtureComponent, Number>): Mixture<U, Numeric<U>> =
            Mixture(entries.toMap().mapValues { Numeric(it.value) })

    }
}

public operator fun <U : UnitsOfMeasurement, T : Amount<U>> Mixture<U, T>.get(component: MixtureComponent): T? = components[component]

public val <U : UnitsOfMeasurement, T : Amount<U>> Mixture<U, T>.fractions: Map<MixtureComponent, Double>
    get() {
        val sum = value
        return components.mapValues { it.value.value / sum }
    }

/**
 * Represents an algebraic structure for managing mixtures of components, where each component is
 * associated with an amount and unit of measurement.
 *
 * @param U The type representing units of measurement for the mixture components.
 * @param T The type representing amounts, tied to the units of measurement.
 * @property componentAlgebra An algebra providing operations on individual components within the mixture.
 * @property one The unity element of this algebra, representing a mixture with pre-defined components and values.
 */
public class MixtureAlgebra<U : UnitsOfMeasurement, T : Amount<U>>(
    public val componentAlgebra: AmountAlgebra<U, T>,
    override val one: Mixture<U, T>
) : AmountAlgebra<U, Mixture<U, T>> {
    override fun Mixture<U, T>.plus(other: Mixture<U, T>): Mixture<U, T> = Mixture(
        with(componentAlgebra) {
            (components.keys + other.components.keys).associateWith {
                (components[it] ?: zero) + (other.components[it] ?: zero)
            }
        }
    )

    override fun Mixture<U, T>.minus(other: Mixture<U, T>): Mixture<U, T> = Mixture(
        with(componentAlgebra) {
            (components.keys + other.components.keys).associateWith {
                (components[it] ?: zero) - (other.components[it] ?: zero)
            }
        }
    )

    override fun Mixture<U, T>.unaryMinus(): Mixture<U, T> = Mixture(
        with(componentAlgebra) {
            components.mapValues { -it.value }
        }
    )

    override fun Mixture<U, T>.times(scale: Number): Mixture<U, T> = Mixture(
        with(componentAlgebra) {
            components.mapValues { it.value * scale }
        }
    )

    override fun Mixture<U, T>.div(scale: Number): Mixture<U, T> = Mixture(
        with(componentAlgebra) {
            components.mapValues { it.value / scale }
        }
    )

    override val zero: Mixture<U, T> = Mixture(emptyMap())
}