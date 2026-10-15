package space.kscience.controls.tagtable

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import space.kscience.controls.constructor.ConstructorPlugin
import space.kscience.controls.constructor.ValueState
import space.kscience.controls.constructor.ValueStateFactory
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.request
import space.kscience.dataforge.io.IOPlugin
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.set
import space.kscience.dataforge.names.Name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

        private val cachedValueStateFactory = super.asValueStateFactory()

        override fun asValueStateFactory(): ValueStateFactory {
            return cachedValueStateFactory
        }
    }

    @Test
    fun testFactoryResolvesTablesRegisteredAfterCatalogWarmup() = runTest(timeout = 5.seconds) {
        val context = Context("tag-warm-catalog") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(IOPlugin)
            plugin(TagTablePlugin)
        }
        try {
            val constructor = context.request(ConstructorPlugin)
            constructor.valueStateFactories
            val tables = context.request(TagTablePlugin)
            val default = CountingTable(context)
            val named = CountingTable(context)
            tables.register(default)
            tables.register(named, "archive")

            assertSame(default.subscribe("sensor"), constructor.buildValueState(Meta {
                "type" put "controls.tags.tagTable"
                set(TagTable.ValueFactorySpec.tag, "sensor")
            }))
            assertSame(named.subscribe("sensor"), constructor.buildValueState(Meta {
                "type" put "controls.tags.tagTable"
                "tagTable" put "archive"
                set(TagTable.ValueFactorySpec.tag, "sensor")
            }))
        } finally {
            context.cancel()
            context.close()
        }
    }

    @Test
    fun testRegistrationKeepsFactoryStableWithoutStarting() = runTest(timeout = 5.seconds) {
        val context = Context("tag-register") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(IOPlugin)
            plugin(TagTablePlugin)
        }
        try {
            val tables = context.request(TagTablePlugin)
            val key = Name.of(TagTable.TAG_TABLE_FACTORY_TYPE)
            val exports = tables.content(ValueStateFactory.PROVIDER_TAGET)
            assertEquals(setOf(key), exports.keys)
            val constructor = context.request(ConstructorPlugin)
            val factory = assertNotNull(constructor.resolveValueStateFactory("controls.tags.tagTable"))
            assertSame(exports[key], factory)
            val default = CountingTable(context)
            val named = CountingTable(context)
            assertSame(default, tables.register(default))
            assertSame(named, tables.register(named, "archive"))
            val parameters = Meta { set(TagTable.ValueFactorySpec.tag, "sensor") }
            val existing = factory.build(context, parameters)
            assertSame(default.subscribe("sensor"), existing)
            val replacement = CountingTable(context)
            tables.register(replacement)

            val updatedExports = tables.content(ValueStateFactory.PROVIDER_TAGET)
            assertEquals(exports.keys, updatedExports.keys)
            assertSame(factory, updatedExports[key])
            assertSame(factory, constructor.resolveValueStateFactory("controls.tags.tagTable"))
            assertSame(replacement.subscribe("sensor"), factory.build(context, parameters))
            assertSame(default.subscribe("sensor"), existing)
            assertNull(constructor.resolveValueStateFactory("controls.tags.tagTable[archive]"))
            runCurrent()
            assertEquals(0, default.starts)
            assertEquals(0, named.starts)
            assertEquals(0, replacement.starts)
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
            val factory = assertNotNull(context.request(ConstructorPlugin).resolveValueStateFactory("controls.tags.tagTable"))
            assertSame(table.subscribe("sensor"), factory.build(context, Meta {
                set(TagTable.ValueFactorySpec.tag, "sensor")
            }))
            assertEquals(0, table.starts)

            table.started.await()
            runCurrent()
            assertEquals(1, table.starts)
        } finally {
            context.cancel()
            context.close()
        }
    }

    @Test
    fun testFactoryUsesBuildContextAndTableOverride() = runTest(timeout = 5.seconds) {
        val context = Context("tag-parent") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(IOPlugin)
            plugin(TagTablePlugin)
        }
        val childTables = TagTablePlugin()
        val child = context.buildContext("tag-child") { plugin(childTables) }
        try {
            val sentinel = ValueState(Meta(42))
            val parameters = Meta {
                set(TagTable.ValueFactorySpec.tagTable, "archive")
                set(TagTable.ValueFactorySpec.tag, "sensor")
            }
            val table = object : TagTable by CountingTable(child) {
                override fun asValueStateFactory(): ValueStateFactory = object : ValueStateFactory {
                    override fun build(context: Context, meta: Meta): ValueState<Meta> {
                        assertSame(child, context)
                        assertSame(parameters, meta)
                        return sentinel
                    }

                    override val descriptor get() = TagTable.ValueFactorySpec.descriptor
                }
            }
            context.request(TagTablePlugin).register(CountingTable(context), "archive")
            childTables.register(table, "archive")
            val factory = assertNotNull(context.request(ConstructorPlugin).resolveValueStateFactory("controls.tags.tagTable"))

            assertSame(sentinel, factory.build(child, parameters))
        } finally {
            child.cancel()
            child.close()
            context.cancel()
            context.close()
        }
    }

    @Test
    fun testFactoryReportsMissingPluginTableAndTag() = runTest(timeout = 5.seconds) {
        val context = Context("tag-errors") {
            coroutineContext(backgroundScope.coroutineContext)
            plugin(IOPlugin)
            plugin(TagTablePlugin)
        }
        val empty = Context("tag-no-plugin") { coroutineContext(backgroundScope.coroutineContext) }
        try {
            val factory = assertNotNull(context.request(ConstructorPlugin).resolveValueStateFactory("controls.tags.tagTable"))
            assertEquals("Tag table plugin is not found in context", assertFailsWith<IllegalStateException> {
                factory.build(empty, Meta.EMPTY)
            }.message)
            assertNull(empty.plugins[TagTablePlugin])
            assertEquals("Tag table not found for tagTable name null", assertFailsWith<IllegalStateException> {
                factory.build(context, Meta.EMPTY)
            }.message)
            val parameters = Meta { set(TagTable.ValueFactorySpec.tagTable, "missing") }
            assertEquals("Tag table not found for tagTable name missing", assertFailsWith<IllegalStateException> {
                factory.build(context, parameters)
            }.message)
            context.request(TagTablePlugin).register(CountingTable(context))
            assertEquals("No tag specified", assertFailsWith<IllegalStateException> {
                factory.build(context, Meta.EMPTY)
            }.message)
        } finally {
            empty.cancel()
            empty.close()
            context.cancel()
            context.close()
        }
    }
}
