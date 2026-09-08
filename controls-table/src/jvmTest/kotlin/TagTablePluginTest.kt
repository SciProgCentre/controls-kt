package space.kscience.controls.tagtable

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kscience.controls.constructor.ConstructorPlugin
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.IOPlugin
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.names.Name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TagTablePluginTest {
    private class CountingTable(context: Context) : TagTable by PlcTagTable(
        context,
        TagTableConfiguration(
            sources = emptyMap(),
            timers = mapOf("scan" to FixedRateTimer(1.seconds)),
            properties = mapOf("sensor" to InternalTagTableColumn("scan", Name.of("source"), "reading")),
        ),
    ) {
        var starts = 0
        val started = CompletableDeferred<Unit>()

        override suspend fun start() {
            starts++
            started.complete(Unit)
        }
    }

    @Test
    fun testRegisterPublishesDefaultAndNamedFactoriesWithoutStarting() = runTest(timeout = 5.seconds) {
        val context = Context("tag-register") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(IOPlugin)
            plugin(TagTablePlugin)
        }
        try {
            val tables = context.request(TagTablePlugin)
            val default = CountingTable(context)
            val named = CountingTable(context)
            assertSame(default, tables.register(default))
            assertSame(named, tables.register(named, "archive"))
            val constructor = context.request(ConstructorPlugin)

            for ((type, table) in mapOf("controls.tags.tagTable" to default, "controls.tags.tagTable[archive]" to named)) {
                assertSame(table, constructor.resolveValueStateFactory(type))
                assertSame(table.valueState("sensor"), constructor.buildValueState(Meta {
                    "type" put type
                    set(TagTable.ValueFactorySpec.tag, "sensor")
                }))
            }
            runCurrent()
            assertEquals(0, default.starts)
            assertEquals(0, named.starts)
        } finally {
            context.cancel()
            context.close()
        }
    }

    @Test
    fun testInstallRegistersBeforeItsSingleStart() = runTest(timeout = 5.seconds) {
        val context = Context("tag-install") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(IOPlugin)
            plugin(TagTablePlugin)
        }
        try {
            val table = CountingTable(context)
            assertSame(table, context.request(TagTablePlugin).install(table))
            assertSame(table, context.request(ConstructorPlugin).resolveValueStateFactory("controls.tags.tagTable"))
            assertEquals(0, table.starts)

            table.started.await()
            runCurrent()
            assertEquals(1, table.starts)
        } finally {
            context.cancel()
            context.close()
        }
    }
}
