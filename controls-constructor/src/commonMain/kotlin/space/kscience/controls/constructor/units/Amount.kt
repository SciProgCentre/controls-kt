package space.kscience.controls.constructor.units

public interface Amount<U : UnitsOfMeasurement> : Comparable<Amount<U>>{
    public val value: Double

    override fun compareTo(other: Amount<U>): Int = value.compareTo(other.value)



}