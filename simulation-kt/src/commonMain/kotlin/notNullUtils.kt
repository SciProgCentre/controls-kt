package space.kscience.simulation

internal inline fun <T, R : Comparable<R>> Iterable<T>.minOfNotNullOrNull(selector: (T) -> R?): R? {
    val iterator = iterator()
    if (!iterator.hasNext()) return null
    var minValue = selector(iterator.next())
    while (iterator.hasNext()) {
        val v = selector(iterator.next())
        when {
            minValue == null -> minValue = v
            v == null -> {/*do nothing*/}
            minValue > v -> {
                minValue = v
            }
        }
    }
    return minValue
}

internal inline fun <T, R : Comparable<R>> Iterable<T>.maxOfNotNullOrNull(selector: (T) -> R?): R? {
    val iterator = iterator()
    if (!iterator.hasNext()) return null
    var maxValue = selector(iterator.next())
    while (iterator.hasNext()) {
        val v = selector(iterator.next())
        when {
            maxValue == null -> maxValue = v
            v == null -> {/*do nothing*/}
            maxValue < v -> {
                maxValue = v
            }
        }
    }
    return maxValue
}