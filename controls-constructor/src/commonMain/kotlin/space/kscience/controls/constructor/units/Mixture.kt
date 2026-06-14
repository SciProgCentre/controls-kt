package space.kscience.controls.constructor.units

import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.MetaConverter
import space.kscience.dataforge.meta.isEmpty
import kotlin.jvm.JvmInline

/**
 * Represents a single component within a mixture.
 *
 * A `MixtureComponent` is identified by its name. This class is primarily
 * used to denote the constituent components of a mixture in applications
 * where tracking or manipulation of mixture constituents is needed.
 */
@JvmInline
public value class MixtureComponent(public val name: String) {
    override fun toString(): String = name
}

/**
 * Represents a mixture of components, where each component is associated with an amount and unit of measurement.
 */
@JvmInline
public value class Mixture<U : UnitsOfMeasurement, T : Amount<U>>(
    public val components: Map<MixtureComponent, T>,
) : Amount<U> {
    override val value: Double get() = components.values.sumOf { it.value }

    public companion object {
        /**
         * Creates a `Mixture` instance from a variable number of components, where each component is paired with its corresponding amount.
         *
         * @param entries Vararg of pairs, where each pair consists of a `MixtureComponent` and an associated `Amount` of that component.
         * @return A `Mixture` containing the specified components and their respective amounts.
         */
        public fun <U : UnitsOfMeasurement, T : Amount<U>> ofAmounts(vararg entries: Pair<MixtureComponent, T>): Mixture<U, T> =
            Mixture(entries.toMap())

        /**
         * Creates a `Mixture` instance where each component is associated with a fractional representation.
         *
         * The fractions are expressed as pairs of `MixtureComponent` and a numeric value,
         * with the numeric value representing the fraction of the component in the mixture in units [U].
         *
         * @param entries Vararg of pairs, where each pair consists of a `MixtureComponent`
         * and a numeric value representing the fraction of the component.
         * @return A `Mixture` containing the specified components with their fractional amounts.
         */
        public fun <U : UnitsOfMeasurement> ofFractions(vararg entries: Pair<MixtureComponent, Number>): Mixture<U, NumericAmount<U>> =
            Mixture(entries.toMap().mapValues { NumericAmount(it.value) })

    }
}

/**
 * Retrieves the amount associated with a specific component in the mixture.
 *
 * @param component The component for which to retrieve the amount.
 * @return The amount associated with the specified component, or `null` if the component is not found.
 */
public operator fun <U : UnitsOfMeasurement, T : Amount<U>> Mixture<U, T>.get(component: MixtureComponent): T? =
    components[component]

/**
 * Retrieves the fraction associated with a specific component in the mixture.
 */
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
 * @param one The identity element for the mixture algebra.
 * @property componentAlgebra An algebra providing operations on individual components within the mixture.
 */
public class MixtureAlgebra<U : UnitsOfMeasurement, T : Amount<U>>(
    public val componentAlgebra: AmountAlgebra<U, T>,
    public val one: Mixture<U, T>
) : AmountAlgebra<U, Mixture<U, T>> {

    override val converter: MetaConverter<Mixture<U, T>> = MetaConverter.mixture(componentAlgebra.converter)

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

    override fun valueOf(number: Number): Mixture<U, T> = one.times(number)

    override val zero: Mixture<U, T> = Mixture(emptyMap())
}

public fun <U : UnitsOfMeasurement, T : Amount<U>> MetaConverter.Companion.mixture(
    fractionConverter: MetaConverter<T>,
): MetaConverter<Mixture<U, T>> = object : MetaConverter<Mixture<U, T>> {
    override fun readOrNull(source: Meta): Mixture<U, T>? {
        if (source.isEmpty()) return null
        return Mixture(
            source.items.map { (key, value) ->
                MixtureComponent(key.toStringUnescaped()) to fractionConverter.read(value)
            }.toMap()
        )
    }

    override fun convert(obj: Mixture<U, T>): Meta = Meta {
        obj.components.forEach { (component, amount) ->
            component.name put fractionConverter.convert(amount)
        }
    }

}