package hep.dataforge.control.demo

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import java.util.concurrent.Executors

val producerDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

fun main() {
    runBlocking {
        val test = MutableStateFlow(0)

        launch {
            var counter = 0
            while (isActive){
                delay(500)
                counter++
                println("produced $counter")
                test.value = counter
            }
        }

        launch(producerDispatcher) {
            test.collect{
                println("collected $it")
            }
        }
    }
}