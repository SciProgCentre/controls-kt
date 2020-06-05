package hep.dataforge.control.demo

import hep.dataforge.meta.MetaItem
import hep.dataforge.meta.double
import hep.dataforge.values.Null
import hep.dataforge.values.asValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val flow: MutableStateFlow<MetaItem<*>> = MutableStateFlow<MetaItem<*>>(MetaItem.ValueItem(Null))

        val collector = launch {
            flow.map { it.double }.collect {
                println(it)
            }
        }

        repeat(10) {
            delay(10)
            flow.value = MetaItem.ValueItem(it.toDouble().asValue())
        }
        collector.cancel()
    }
}