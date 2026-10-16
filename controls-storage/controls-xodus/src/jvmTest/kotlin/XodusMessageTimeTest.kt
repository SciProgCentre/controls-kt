import jetbrains.exodus.entitystore.PersistentEntityStores
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.PropertyChangedMessage
import space.kscience.controls.storage.read
import space.kscience.controls.xodus.XodusDeviceMessageStorage
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.names.Name
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

internal class XodusMessageTimeTest {
    @Test
    fun readsTimeRangeWithoutOverwritingTargetAfterReopen() = runTest {
        val directory = Files.createTempDirectory("controls-xodus-message-time")
        val source = Name.of("source")
        val target = Name.of("target")
        val messages = (1..5).map { second ->
            PropertyChangedMessage(
                time = Instant.fromEpochSeconds(second.toLong()),
                property = "value",
                value = Meta(second),
                sourceDevice = source,
                targetDevice = if (second == 3) Name.of("other-target") else target,
            )
        } + PropertyChangedMessage(
            time = Instant.fromEpochSeconds(3),
            property = "value",
            value = Meta(99),
            sourceDevice = Name.of("other-source"),
            targetDevice = target,
        )
        val range = Instant.fromEpochSeconds(2)..Instant.fromEpochSeconds(4)
        val expected = listOf(messages[1], messages[3])

        try {
            XodusDeviceMessageStorage(PersistentEntityStores.newInstance(directory.toFile())).use { storage ->
                messages.forEach { storage.write(it) }
            }
            XodusDeviceMessageStorage(PersistentEntityStores.newInstance(directory.toFile())).use { storage ->
                assertEquals(expected, storage.read(range, source, target).toList())
                assertEquals(expected, storage.read<PropertyChangedMessage>(range, source, target).toList().sortedBy { it.time })
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
