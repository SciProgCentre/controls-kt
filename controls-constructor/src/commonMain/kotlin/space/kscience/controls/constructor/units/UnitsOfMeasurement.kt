package space.kscience.controls.constructor.units


public interface UnitsOfMeasurement {
    public val displayName: String get() = this::class.simpleName ?: "UnnamedUnits"
}

/**
 * Units of measurement that are not associated with physical quantity of matter
 */
public interface UnitsOfMatter : UnitsOfMeasurement

/**/

public interface UnitsOfLength : UnitsOfMeasurement

public data object Meters : UnitsOfLength

/**/

public interface UnitsOfTime : UnitsOfMeasurement

public data object Seconds : UnitsOfTime

/**/

public interface UnitsOfVelocity : UnitsOfMeasurement

public data object MetersPerSecond : UnitsOfVelocity

/**/

public sealed interface UnitsOfAngles : UnitsOfMeasurement

public data object Radians : UnitsOfAngles
public data object Degrees : UnitsOfAngles

/**/

public sealed interface UnitsAngularOfVelocity : UnitsOfMeasurement

public data object RadiansPerSecond : UnitsAngularOfVelocity, NumericAmountAlgebra<RadiansPerSecond>() {
    override val units: RadiansPerSecond get() = this
}

public data object DegreesPerSecond : UnitsAngularOfVelocity, NumericAmountAlgebra<DegreesPerSecond>() {
    override val units: DegreesPerSecond get() = this
}

/**/
public interface UnitsOfForce : UnitsOfMeasurement

public data object Newtons : UnitsOfForce, NumericAmountAlgebra<Newtons>() {
    override val units: Newtons get() = this
}

/**/

public interface UnitsOfTorque : UnitsOfMeasurement

public data object NewtonsMeters : UnitsOfTorque, NumericAmountAlgebra<NewtonsMeters>() {
    override val units: NewtonsMeters get() = this
}

/**/

public interface UnitsOfVolume : UnitsOfMatter

public data object CubicMeters : UnitsOfVolume, NumericAmountAlgebra<CubicMeters>() {
    override val units: CubicMeters get() = this
}

/**/

public interface UnitsOfMass : UnitsOfMatter

public data object Kilograms : UnitsOfMass, NumericAmountAlgebra<Kilograms>() {
    override val units: Kilograms get() = this
}

public val Number.kilograms: NumericAmount<Kilograms> get() = NumericAmount<Kilograms>(toDouble())

/**/

public interface UnitsOfMomentOfInertia : UnitsOfMeasurement

public data object KgM2 : UnitsOfMomentOfInertia, NumericAmountAlgebra<KgM2>() {
    override val units: KgM2 get() = this
}

/**/

public data object Mole : UnitsOfMatter, NumericAmountAlgebra<Mole>() {
    override val units: Mole get() = this
}