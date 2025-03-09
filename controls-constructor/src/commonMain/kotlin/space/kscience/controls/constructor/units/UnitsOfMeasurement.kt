package space.kscience.controls.constructor.units


public interface UnitsOfMeasurement

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

public data object RadiansPerSecond : UnitsAngularOfVelocity

public data object DegreesPerSecond : UnitsAngularOfVelocity

/**/
public interface UnitsOfForce: UnitsOfMeasurement

public data object Newtons: UnitsOfForce

/**/

public interface UnitsOfTorque: UnitsOfMeasurement

public data object NewtonsMeters: UnitsOfTorque

/**/

public interface UnitsOfMass: UnitsOfMeasurement

public data object Kilograms : UnitsOfMass

/**/

public interface UnitsOfMomentOfInertia: UnitsOfMeasurement

public data object KgM2: UnitsOfMomentOfInertia