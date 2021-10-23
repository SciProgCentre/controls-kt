package ru.mipt.npm.controls.ports

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


internal class PortIOTest{

    @Test
    fun testDelimiteredByteArrayFlow(){
        val flow = flowOf("bb?b","ddd?",":defgb?:ddf","34fb?:--").map { it.encodeToByteArray() }
        val chunked = flow.withDelimiter("?:".encodeToByteArray())
        runBlocking {
            val result = chunked.toList()
            assertEquals(3, result.size)
            assertEquals("bb?bddd?:",result[0].decodeToString())
            assertEquals("defgb?:", result[1].decodeToString())
            assertEquals("ddf34fb?:", result[2].decodeToString())
        }
    }
}