@file:Suppress("UNUSED_PARAMETER", "RedundantVisibilityModifier")

package space.kscience.controls.spec

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.test.runTest
import space.kscience.controls.api.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.context.info
import space.kscience.dataforge.context.logger
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.names.asName
import space.kscience.dataforge.names.parseAsName
import kotlin.test.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import space.kscience.controls.manager.DeviceManager
import space.kscience.dataforge.context.AbstractPlugin
import space.kscience.dataforge.context.PluginTag
import space.kscience.dataforge.context.PluginTag.Companion.DATAFORGE_GROUP
import space.kscience.dataforge.context.request
import space.kscience.magix.api.MagixEndpoint
import space.kscience.magix.api.MagixFormat
import space.kscience.magix.api.MagixMessage
import space.kscience.magix.api.MagixMessageFilter
import kotlin.time.Duration.Companion.seconds

class CompositeControlTest {

    // ---------------------- Device Specifications ----------------------------------

    /**
     * Specification for a simple stepper motor device.
     */
    public object StepperMotorSpec : DeviceSpecification<StepperMotorDevice>(
        deviceFactory = { context, meta -> StepperMotorDevice(context, meta) }
    ) {
        public val position by intProperty(
            name = "position",
            read = { getPosition() },
            write = { _, value -> setPosition(value) }
        )

        public val maxPosition by intProperty(
            name = "maxPosition",
            read = { maxPosition }
        )
    }

    /**
     * Spec for a valve device.
     */
    public object ValveSpec : DeviceSpecification<ValveDevice>(
        deviceFactory = { context, meta -> ValveDevice(context, meta) }
    ) {
        public val state by booleanProperty(
            read = { getState() },
            write = { _, value -> setState(value) }
        )
    }

    /**
     * Spec for a pressure chamber device.
     */
    public object PressureChamberSpec : DeviceSpecification<PressureChamberDevice>(
        deviceFactory = { context, meta -> PressureChamberDevice(context, meta) }
    ) {
        public val pressure by doubleProperty(
            read = { getPressure() },
            write = { _, value -> setPressure(value) }
        )
    }

    /**
     * Spec for a syringe pump device.
     */
    public object SyringePumpSpec : DeviceSpecification<SyringePumpDevice>(
        deviceFactory = { context, meta -> SyringePumpDevice(context, meta) }
    ) {
        public val volume by doubleProperty(
            read = { getVolume() },
            write = { _, value -> setVolume(value) }
        )
    }

    /**
     * Spec for a reagent sensor device.
     */
    public object ReagentSensorSpec : DeviceSpecification<ReagentSensorDevice>(
        deviceFactory = { context, meta -> ReagentSensorDevice(context, meta) }
    ) {
        public val isPresent by booleanProperty(
            read = { checkReagent() }
        )
    }

    /**
     * Spec for a needle device.
     */
    public object NeedleSpec : DeviceSpecification<NeedleDevice>(
        deviceFactory = { context, meta -> NeedleDevice(context, meta) }
    ) {
        public val mode by enumProperty<NeedleDevice.Mode, NeedleDevice>(
            read = { getMode() },
            write = { _, value -> setMode(value) }
        )

        public val position by doubleProperty(
            read = { getPosition() },
            write = { _, value -> setPosition(value) }
        )
    }

    /**
     * Spec for a shaker device (contains vertical/horizontal stepper motors).
     */
    public object ShakerSpec : DeviceSpecification<ShakerDevice>(
        deviceFactory = { context, meta -> ShakerDevice(context, meta) }
    ) {
        public val verticalMotor by childSpec(StepperMotorSpec)
        public val horizontalMotor by childSpec(StepperMotorSpec)
    }

    /**
     * Spec for a transportation system (slide/push/receive motors).
     */
    public object TransportationSystemSpec : DeviceSpecification<TransportationSystem>(
        deviceFactory = { context, meta -> TransportationSystem(context, meta) }
    ) {
        public val slideMotor by childSpec(StepperMotorSpec)
        public val pushMotor by childSpec(StepperMotorSpec)
        public val receiveMotor by childSpec(StepperMotorSpec)
    }

    /**
     * Spec for the full analyzer device, containing multiple child devices:
     * transportation system, shaker, needle, valves, pressure chambers,
     * syringe pumps, reagent sensors.
     */
    public object AnalyzerSpec : DeviceSpecification<AnalyzerDevice>(
        deviceFactory = { context, meta -> AnalyzerDevice(context, meta) }
    ) {
        public val transportationSystem by childSpec(TransportationSystemSpec)
        public val shakerDevice by childSpec(ShakerSpec)
        public val needleDevice by childSpec(NeedleSpec)

        public val valveV20 by childSpec(ValveSpec)
        public val valveV17 by childSpec(ValveSpec)
        public val valveV18 by childSpec(ValveSpec)
        public val valveV35 by childSpec(ValveSpec)

        public val pressureChamberHigh by childSpec(PressureChamberSpec)
        public val pressureChamberLow by childSpec(PressureChamberSpec)

        public val syringePumpMA100 by childSpec(SyringePumpSpec)
        public val syringePumpMA25 by childSpec(SyringePumpSpec)

        public val reagentSensor1 by childSpec(ReagentSensorSpec)
        public val reagentSensor2 by childSpec(ReagentSensorSpec)
        public val reagentSensor3 by childSpec(ReagentSensorSpec)
    }

    // ---------------------- Device Implementations ----------------------------------

    /**
     * A simple stepper motor device that can move between [0..maxPosition].
     */
    public class StepperMotorDevice(
        context: Context,
        meta: Meta = Meta.EMPTY
    ) : ConfigurableCompositeControlComponent<StepperMotorDevice>(StepperMotorSpec, context, meta) {

        private var _position: Int = 0
        public val maxPosition: Int = meta["maxPosition".parseAsName()].int ?: 100

        /**
         * Get current position of the stepper motor.
         * @return Current position as Int
         */
        public suspend fun getPosition(): Int = _position

        /**
         * Set position of the stepper motor, if position is valid the move will occur.
         * @param value target position as Int
         */
        public suspend fun setPosition(value: Int) {
            if (value in 0..maxPosition) {
                _position = value
                logger.info { "StepperMotorDevice: Moving to position $_position" }
                delay(100)
            } else {
                logger.info { "StepperMotorDevice: Invalid position $value (max: $maxPosition)" }
            }
        }
    }

    /**
     * A device representing a valve that can be open/closed.
     */
    public class ValveDevice(
        context: Context,
        meta: Meta = Meta.EMPTY
    ) : ConfigurableCompositeControlComponent<ValveDevice>(ValveSpec, context, meta) {

        private var _state: Boolean = false

        /**
         * Get current state of the valve
         * @return true if valve is open, false if closed
         */
        public suspend fun getState(): Boolean = _state

        /**
         * Set the current state of the valve and print the change.
         * @param value true if valve should be open, false if should be closed
         */
        public suspend fun setState(value: Boolean) {
            _state = value
            val stateStr = if (_state) "open" else "closed"
            logger.info { "ValveDevice: Valve is now $stateStr" }
            delay(50)
        }

        /**
         * Simulates clicking the valve.
         */
        public suspend fun click() {
            logger.info { "ValveDevice: Clicking valve..." }
            setState(true)
            delay(50)
            setState(false)
            logger.info { "ValveDevice: Valve click completed" }
        }
    }

    /**
     * A device for controlling pressure in a chamber.
     */
    public class PressureChamberDevice(
        context: Context,
        meta: Meta = Meta.EMPTY
    ) : ConfigurableCompositeControlComponent<PressureChamberDevice>(PressureChamberSpec, context, meta) {

        private var _pressure: Double = 0.0

        /**
         * Get the current pressure in the chamber.
         * @return current pressure as Double
         */
        public suspend fun getPressure(): Double = _pressure

        /**
         * Set the pressure in the chamber.
         * @param value target pressure as Double
         */
        public suspend fun setPressure(value: Double) {
            _pressure = value
            logger.info { "PressureChamberDevice: Pressure is now $_pressure" }
            delay(50)
        }
    }

    /**
     * A device controlling a syringe pump with a [maxVolume].
     */
    public class SyringePumpDevice(
        context: Context,
        meta: Meta = Meta.EMPTY
    ) : ConfigurableCompositeControlComponent<SyringePumpDevice>(SyringePumpSpec, context, meta) {

        private var _volume: Double = 0.0
        public val maxVolume: Double = meta["maxVolume".parseAsName()].double ?: 5.0

        /**
         * Get current volume in the syringe
         * @return volume as Double
         */
        public suspend fun getVolume(): Double = _volume

        /**
         * Set the current volume in the syringe.
         * @param value the target volume as Double
         */
        public suspend fun setVolume(value: Double) {
            if (value in 0.0..maxVolume) {
                _volume = value
                logger.info { "SyringePumpDevice: Volume is now $_volume ml" }
                delay(100)
            } else {
                logger.info { "SyringePumpDevice: Invalid volume $value (max: $maxVolume)" }
            }
        }
    }

    /**
     * A reagent sensor that can check presence of reagent (mocked as always true).
     * Could be overridden or replaced by a mock device that returns false.
     */
    public class ReagentSensorDevice(
        context: Context,
        meta: Meta = Meta.EMPTY
    ) : ConfigurableCompositeControlComponent<ReagentSensorDevice>(ReagentSensorSpec, context, meta) {

        /**
         * Checks for reagent presence.
         * @return true if reagent is present.
         */
        public suspend fun checkReagent(): Boolean {
            logger.info { "ReagentSensorDevice: Checking for reagent presence..." }
            delay(100)
            val isPresent = true
            logger.info { "ReagentSensorDevice: Reagent is ${if (isPresent) "present" else "not present"}" }
            return isPresent
        }
    }

    /**
     * A needle device that can move within [0..100] mm, switch mode (SAMPLING/WASHING),
     * and perform washing or sampling processes.
     */
    public class NeedleDevice(
        context: Context,
        meta: Meta = Meta.EMPTY
    ) : ConfigurableCompositeControlComponent<NeedleDevice>(NeedleSpec, context, meta) {

        public enum class Mode { SAMPLING, WASHING }

        private var _mode: Mode = Mode.WASHING
        private var _position: Double = 0.0

        /**
         * Get the current mode of needle.
         * @return current mode of the needle.
         */
        public suspend fun getMode(): Mode = _mode

        /**
         * Set the mode of the needle
         * @param value the target mode
         */
        public suspend fun setMode(value: Mode) {
            _mode = value
            logger.info { "NeedleDevice: Mode is now $_mode" }
            delay(50)
        }

        /**
         * Get current position of the needle
         * @return current position as Double
         */
        public suspend fun getPosition(): Double = _position

        /**
         * Set the needle position
         * @param value target position as Double
         */
        public suspend fun setPosition(value: Double) {
            if (value in 0.0..100.0) {
                _position = value
                logger.info { "NeedleDevice: Moved to position $_position mm" }
                delay(100)
            } else {
                logger.info { "NeedleDevice: Invalid position $value mm" }
            }
        }

        /**
         * Executes washing process for given duration
         * @param duration time for washing in seconds
         */
        public suspend fun performWashing(duration: Int) {
            logger.info { "NeedleDevice: Washing in progress for $duration seconds" }
            delay(duration * 1000L)
        }

        /**
         * Execute sampling procedure
         */
        public suspend fun performSampling() {
            logger.info { "NeedleDevice: Performing sample intake at position $_position mm" }
            delay(500)
        }
    }

    /**
     * A shaker device containing two stepper motors (vertical/horizontal).
     */
    public class ShakerDevice(
        context: Context,
        meta: Meta = Meta.EMPTY
    ) : ConfigurableCompositeControlComponent<ShakerDevice>(ShakerSpec, context, meta) {

        /**
         * Get vertical stepper motor
         */
        public val verticalMotor by childDevice<StepperMotorDevice>()

        /**
         * Get horizontal stepper motor
         */
        public val horizontalMotor by childDevice<StepperMotorDevice>()

        /**
         * Shakes the device for given cycles.
         * @param cycles amount of cycles for shaking
         */
        public suspend fun shake(cycles: Int) {
            logger.info { "ShakerDevice: Shaking started, cycles: $cycles" }
            repeat(cycles) {
                verticalMotor.setPosition(3)
                verticalMotor.setPosition(1)
                logger.info { "ShakerDevice: cycle ${it + 1} done" }
            }
            logger.info { "ShakerDevice: Shaking completed" }
        }
    }

    /**
     * A system with three stepper motors for various mechanical movements.
     */
    public class TransportationSystem(
        context: Context,
        meta: Meta = Meta.EMPTY
    ) : ConfigurableCompositeControlComponent<TransportationSystem>(TransportationSystemSpec, context, meta) {

        public val slideMotor by childDevice<StepperMotorDevice>()
        public val pushMotor by childDevice<StepperMotorDevice>()
        public val receiveMotor by childDevice<StepperMotorDevice>()
    }

    /**
     * The main analyzer device, containing various child devices: motors, valves,
     * syringes, sensors, etc. Has methods for "processSample", "calibrate", "executeRecipeX"...
     */
    public class AnalyzerDevice(
        context: Context,
        meta: Meta = Meta.EMPTY
    ) : ConfigurableCompositeControlComponent<AnalyzerDevice>(AnalyzerSpec, context, meta) {

        public val transportationSystem by childDevice<TransportationSystem>()
        public val shakerDevice by childDevice<ShakerDevice>()
        public val needleDevice by childDevice<NeedleDevice>()

        public val valveV20 by childDevice<ValveDevice>()
        public val valveV17 by childDevice<ValveDevice>()
        public val valveV18 by childDevice<ValveDevice>()
        public val valveV35 by childDevice<ValveDevice>()

        public val pressureChamberHigh by childDevice<PressureChamberDevice>()
        public val pressureChamberLow by childDevice<PressureChamberDevice>()

        public val syringePumpMA100 by childDevice<SyringePumpDevice>()
        public val syringePumpMA25 by childDevice<SyringePumpDevice>()

        public val reagentSensor1 by childDevice<ReagentSensorDevice>()
        public val reagentSensor2 by childDevice<ReagentSensorDevice>()
        public val reagentSensor3 by childDevice<ReagentSensorDevice>()

        /**
         * Example method: process a sample by opening/closing valves, moving syringe volumes, etc.
         */
        public suspend fun processSample() {
            logger.info { "AnalyzerDevice: The beginning of the sampling process" }

            valveV20.setState(true)
            syringePumpMA100.setVolume(0.1)
            delay(500)
            valveV20.setState(false)

            valveV17.setState(true)
            syringePumpMA25.setVolume(2.5)
            delay(500)
            valveV17.setState(false)

            syringePumpMA100.setVolume(0.0)
            syringePumpMA25.setVolume(0.0)

            logger.info { "AnalyzerDevice: The sampling process is completed" }
        }

        /**
         * Example "calibration" procedure which manipulates motors, valves, sensors, pumps, etc.
         */
        public suspend fun calibrate() {
            logger.info { "AnalyzerDevice: The beginning of calibration..." }

            val motors = listOf(
                transportationSystem.slideMotor,
                transportationSystem.pushMotor,
                transportationSystem.receiveMotor,
                shakerDevice.verticalMotor,
                shakerDevice.horizontalMotor,
            )

            for (motor in motors) {
                for (pos in 0..motor.maxPosition) {
                    motor.setPosition(pos)
                }
                motor.setPosition(0)
            }

            val valves = listOf(valveV20, valveV17, valveV18, valveV35)
            for (valve in valves) {
                valve.click()
                valve.setState(false)
            }

            pressureChamberHigh.setPressure(2.0)
            pressureChamberLow.setPressure(-1.0)

            val sensors = listOf(reagentSensor1, reagentSensor2, reagentSensor3)
            for (sensor in sensors) {
                sensor.checkReagent()
            }

            val pumps = listOf(syringePumpMA100, syringePumpMA25)
            for (pump in pumps) {
                repeat(5) {
                    pump.setVolume(pump.maxVolume)
                    pump.setVolume(0.0)
                }
            }

            needleDevice.setPosition(0.0)
            needleDevice.setMode(NeedleDevice.Mode.WASHING)
            needleDevice.performWashing(5)

            logger.info { "AnalyzerDevice: Calibration is completed" }
        }

        /**
         * Demonstration recipe #1: moves the slide motor, manipulates the shaker, and does sampling.
         */
        public suspend fun executeRecipe1() {
            logger.info { "AnalyzerDevice: Executing recipe 1" }
            val currentSlidePosition = transportationSystem.slideMotor.getPosition()
            transportationSystem.slideMotor.setPosition(currentSlidePosition + 1)
            logger.info { "Moved a slide to position ${currentSlidePosition + 1}" }

            logger.info { "Capturing tube for mixing" }
            shakerDevice.verticalMotor.setPosition(1)
            shakerDevice.horizontalMotor.setPosition(1)
            shakerDevice.horizontalMotor.setPosition(2)
            shakerDevice.verticalMotor.setPosition(2)
            shakerDevice.shake(5)
            logger.info { "Shaker: movement done" }

            executeSampling()
            needleDevice.setPosition(0.0)
            logger.info { "Needle moved to its initial position" }
        }

        /**
         * Demonstration recipe #2: an "automatic measurement".
         */
        public suspend fun executeRecipe2() {
            logger.info { "AnalyzerDevice: Executing Recipe 2 - Automatic Measurement" }

            transportationSystem.receiveMotor.setPosition(
                transportationSystem.receiveMotor.getPosition() + 1
            )

            if (!checkTrayInPushSystem()) {
                logger.info { "Tray missing. Trying to move again" }
                transportationSystem.receiveMotor.setPosition(
                    transportationSystem.receiveMotor.getPosition() + 1
                )
            } else {
                executeSampling()
            }

            if (transportationSystem.receiveMotor.getPosition() >= transportationSystem.receiveMotor.maxPosition) {
                logger.info { "Plate is complete. Resetting pusher to initial position" }
                transportationSystem.receiveMotor.setPosition(0)
            }

            logger.info { "Recipe 2 execution finished" }
            needleDevice.setPosition(0.0)
            logger.info { "Needle moved to its initial position" }
        }

        /**
         * Demonstration recipe #3: a simpler single measurement.
         */
        public suspend fun executeRecipe3() {
            logger.info { "AnalyzerDevice: Executing Recipe 3 - Single measurement" }
            executeSampling()
            logger.info { "AnalyzerDevice: Recipe 3 completed" }
            needleDevice.setPosition(0.0)
            logger.info { "Needle moved to its initial position" }
        }

        /**
         * Simulates checking if a tray is present in the push system.
         */
        private suspend fun checkTrayInPushSystem(): Boolean {
            logger.info { "Checking for a tray in a pushing system" }
            delay(200)
            return true
        }

        private suspend fun executeSampling() {
            needleDevice.setMode(NeedleDevice.Mode.SAMPLING)
            needleDevice.performSampling()
            needleDevice.setMode(NeedleDevice.Mode.WASHING)
            needleDevice.performWashing(2)
        }
    }

    private fun createTestContext() = Context("test") {
        plugin(DeviceHubManager)
    }

    @Test
    fun `test StepperMotorDevice position setting`() = runTest {
        val context = createTestContext()
        val motor = StepperMotorDevice(context, Meta { "maxPosition" put 500 })

        motor.setPosition(200)
        assertEquals(200, motor.getPosition(), "Position should be set correctly")

        motor.setPosition(0)
        assertEquals(0, motor.getPosition(), "Position should be reset to 0")

        motor.setPosition(500)
        assertEquals(500, motor.getPosition(), "Position should be set to max value")
    }

    @Test
    fun `test StepperMotorDevice invalid position`() = runTest {
        val context = createTestContext()
        val motor = StepperMotorDevice(context, Meta { "maxPosition" put 100 })

        motor.setPosition(200) // Should be outside the range
        assertEquals(0, motor.getPosition(), "Position should not be changed for invalid value")
    }

    @Test
    fun `test ValveDevice state toggling`() = runTest {
        val context = createTestContext()
        val valve = ValveDevice(context)

        assertFalse(valve.getState(), "Initial state should be closed")

        valve.setState(true)
        assertTrue(valve.getState(), "State should be set to open")

        valve.setState(false)
        assertFalse(valve.getState(), "State should be set to closed")
    }

    @Test
    fun `test ValveDevice click operation`() = runTest {
        val context = createTestContext()
        val valve = ValveDevice(context)

        assertFalse(valve.getState(), "Initial state should be closed")

        valve.click()
        // After click, valve should end in closed state
        assertFalse(valve.getState(), "Valve should be closed after click")
    }

    @Test
    fun `test PressureChamberDevice pressure setting`() = runTest {
        val context = createTestContext()
        val chamber = PressureChamberDevice(context)

        chamber.setPressure(1.5)
        assertEquals(1.5, chamber.getPressure(), "Pressure should be set correctly")

        chamber.setPressure(0.0)
        assertEquals(0.0, chamber.getPressure(), "Pressure should be 0")

        chamber.setPressure(-1.0)
        assertEquals(-1.0, chamber.getPressure(), "Pressure can be negative")
    }

    @Test
    fun `test SyringePumpDevice volume setting`() = runTest {
        val context = createTestContext()
        val pump = SyringePumpDevice(context, Meta { "maxVolume" put 10.0 })

        pump.setVolume(3.5)
        assertEquals(3.5, pump.getVolume(), "Volume should be set correctly")

        pump.setVolume(0.0)
        assertEquals(0.0, pump.getVolume(), "Volume should be reset to 0")

        pump.setVolume(10.0)
        assertEquals(10.0, pump.getVolume(), "Volume should be set to max value")
    }

    @Test
    fun `test SyringePumpDevice invalid volume`() = runTest {
        val context = createTestContext()
        val pump = SyringePumpDevice(context, Meta { "maxVolume" put 5.0 })

        pump.setVolume(10.0)
        assertEquals(0.0, pump.getVolume(), "Volume should remain 0 on invalid value")
    }

    @Test
    fun `test ReagentSensorDevice checkReagent returns true`() = runTest {
        val context = createTestContext()
        val sensor = ReagentSensorDevice(context)

        assertTrue(sensor.checkReagent(), "Reagent sensor should report presence by default")
    }

    @Test
    fun `test NeedleDevice position and mode setting`() = runTest {
        val context = createTestContext()
        val needle = NeedleDevice(context)

        needle.setMode(NeedleDevice.Mode.SAMPLING)
        assertEquals(NeedleDevice.Mode.SAMPLING, needle.getMode(), "Mode should be SAMPLING")

        needle.setMode(NeedleDevice.Mode.WASHING)
        assertEquals(NeedleDevice.Mode.WASHING, needle.getMode(), "Mode should be WASHING")

        needle.setPosition(50.0)
        assertEquals(50.0, needle.getPosition(), "Position should be set to 50")

        needle.setPosition(0.0)
        assertEquals(0.0, needle.getPosition(), "Position should be 0")

        needle.setPosition(100.0)
        assertEquals(100.0, needle.getPosition(), "Position should be set to 100")
    }

    @Test
    fun `test NeedleDevice invalid position`() = runTest {
        val context = createTestContext()
        val needle = NeedleDevice(context)

        needle.setPosition(200.0)
        assertEquals(0.0, needle.getPosition(), "Needle position should remain 0 on invalid input")
    }

    @Test
    fun `test ShakerDevice shaking`() = runTest {
        val context = createTestContext()
        val shaker = ShakerDevice(context)

        shaker.initChildren()
        shaker.start()

        shaker.shake(2)
        val verticalMotorPosition = shaker.verticalMotor.getPosition()
        val horizontalMotorPosition = shaker.horizontalMotor.getPosition()

        assertEquals(1, verticalMotorPosition, "Vertical motor expected to end at position 1.")
        assertEquals(0, horizontalMotorPosition, "Horizontal motor expected to be 0 after shaking cycles.")
    }

    @Test
    fun `test TransportationSystem motors existence`() = runTest {
        val context = createTestContext()
        val transportationSystem = TransportationSystem(context)

        transportationSystem.initChildren()
        transportationSystem.start()

        assertNotNull(transportationSystem.slideMotor, "slideMotor should exist")
        assertNotNull(transportationSystem.pushMotor, "pushMotor should exist")
        assertNotNull(transportationSystem.receiveMotor, "receiveMotor should exist")
    }

    @Test
    fun `test AnalyzerDevice device access`() = runTest {
        val context = createTestContext()
        val analyzer = AnalyzerDevice(context)
        analyzer.initChildren()
        analyzer.start()

        assertNotNull(analyzer.transportationSystem, "Transportation system should exist")
        assertNotNull(analyzer.shakerDevice, "Shaker device should exist")
        assertNotNull(analyzer.needleDevice, "Needle device should exist")
        assertNotNull(analyzer.valveV20, "Valve V20 should exist")
        assertNotNull(analyzer.valveV17, "Valve V17 should exist")
        assertNotNull(analyzer.valveV18, "Valve V18 should exist")
        assertNotNull(analyzer.valveV35, "Valve V35 should exist")
        assertNotNull(analyzer.pressureChamberHigh, "High pressure chamber should exist")
        assertNotNull(analyzer.pressureChamberLow, "Low pressure chamber should exist")
        assertNotNull(analyzer.syringePumpMA100, "Syringe pump MA100 should exist")
        assertNotNull(analyzer.syringePumpMA25, "Syringe pump MA25 should exist")
        assertNotNull(analyzer.reagentSensor1, "Reagent sensor 1 should exist")
        assertNotNull(analyzer.reagentSensor2, "Reagent sensor 2 should exist")
        assertNotNull(analyzer.reagentSensor3, "Reagent sensor 3 should exist")
    }

    @Test
    fun `test add and remove device using manager`() = runTest {
        val context = createTestContext()
        val manager = DeviceHubManager(context, MagixMessageBusStub())

        val motor = StepperMotorDevice(context)
        val name = "motorTest".asName()

        manager.attachDevice(name, motor, DeviceLifecycleConfig(), null, StartMode.SYNC)
        assertTrue(name in manager.devices.keys, "The device should be in the manager after add.")

        manager.detachDevice(name, waitStop = true)
        assertFalse(name in manager.devices.keys, "The device should be removed from the manager.")

        manager.shutdown()
    }

    @Test
    fun `test batch start and stop`() = runTest {
        val context = createTestContext()
        val manager = DeviceHubManager(context, MagixMessageBusStub())
        val config = DeviceLifecycleConfig()

        val motor1 = StepperMotorDevice(context)
        val motor2 = StepperMotorDevice(context)

        manager.attachDevice("m1".asName(), motor1, config, null, StartMode.SYNC)
        manager.attachDevice("m2".asName(), motor2, config, null, StartMode.SYNC)

        val stopSuccess = manager.stopDevicesBatch(listOf("m1".asName(), "m2".asName()))
        assertTrue(stopSuccess, "Batch stop should succeed for normal devices")

        assertEquals(LifecycleState.STOPPED, motor1.lifecycleState, "m1 should be STOPPED after stop")
        assertEquals(LifecycleState.STOPPED, motor2.lifecycleState, "m2 should be STOPPED after stop")

        val startSuccess = manager.startDevicesBatch(listOf("m1".asName(), "m2".asName()))
        assertTrue(startSuccess, "Batch start should succeed")

        assertEquals(LifecycleState.STARTED, motor1.lifecycleState, "m1 should be STARTED")
        assertEquals(LifecycleState.STARTED, motor2.lifecycleState, "m2 should be STARTED")
        manager.shutdown()
    }

    @Test
    fun `test hot swap device`() = runTest {
        val context = createTestContext()
        val manager = DeviceHubManager(context, MagixMessageBusStub())

        val oldDevice = StepperMotorDevice(context, Meta { "maxPosition" put 100 })
        val name = "motorSwap".asName()
        manager.attachDevice(name, oldDevice, DeviceLifecycleConfig(), null, StartMode.SYNC)
        assertEquals(100, oldDevice.maxPosition, "Old motor should have maxPosition=100")

        val newDevice = StepperMotorDevice(context, Meta { "maxPosition" put 999 })

        manager.hotSwapDevice(
            name,
            newDevice,
            DeviceLifecycleConfig(),
            newMeta = null
        )

        val current = manager.devices[name]
        assertNotNull(current, "New device should be present after hot swap")
        assertSame(newDevice, current, "Manager should reference the new device instance")
        assertEquals(999, newDevice.maxPosition)

        manager.detachDevice(name, waitStop = true)
        manager.shutdown()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `test Analyzer processSample with parallel sensor reading`() = runTest {
        val context = createTestContext()
        val analyzer = AnalyzerDevice(context)
        analyzer.initChildren()
        analyzer.start()

        val sensorJob = launch {
            analyzer.reagentSensor1.checkReagent()
            advanceTimeBy(150)
            analyzer.reagentSensor2.checkReagent()
        }

        val processJob = launch { analyzer.processSample() }

        sensorJob.join()
        processJob.join()

        assertFalse(analyzer.valveV20.getState(), "valveV20 should be closed at end")
        assertEquals(0.0, analyzer.syringePumpMA100.getVolume(), "MA100 pump volume should be 0.0 at end")
        assertEquals(0.0, analyzer.syringePumpMA25.getVolume(), "MA25 pump volume should be 0.0 at end")
    }

    @Test
    fun `test error handling with custom error handler`() = runTest {
        val context = createTestContext()
        val manager = DeviceHubManager(context, MagixMessageBusStub())

        val errorConfig = DeviceLifecycleConfig(
            onError = ChildDeviceErrorHandler.PROPAGATE
        )
        val motor = StepperMotorDevice(context)
        val name = "failingMotor".asName()

        manager.attachDevice(name, motor, errorConfig, null, StartMode.SYNC)

        runCatching {
            motor.setPosition(9999)
        }

        assertTrue(name in manager.devices.keys, "Device still in manager")
        manager.shutdown()
    }

    @Test
    fun `test transaction execution and rollback`() = runTest {
        val context = createTestContext()
        val manager = DeviceHubManager(context, MagixMessageBusStub())

        val analyzer = AnalyzerDevice(context)
        analyzer.initChildren()
        analyzer.start()

        val txManager = manager.transactionManager

        val initialV20State = analyzer.valveV20.getState()
        val initialV17State = analyzer.valveV17.getState()

        txManager.withTransaction { txContext ->
            analyzer.valveV20.setState(true)
            analyzer.valveV17.setState(true)

            txContext.recordAction(object : ReversibleAction {
                override val id = "valve_v20_reset"
                override suspend fun reverse() {
                    analyzer.valveV20.setState(initialV20State)
                }
            })

            txContext.recordAction(object : ReversibleAction {
                override val id = "valve_v17_reset"
                override suspend fun reverse() {
                    analyzer.valveV17.setState(initialV17State)
                }
            })

            true
        }

        assertTrue(analyzer.valveV20.getState(), "V20 should be open after transaction")
        assertTrue(analyzer.valveV17.getState(), "V17 should be open after transaction")

        try {
            txManager.withTransaction { txContext ->
                analyzer.valveV20.setState(false)
                analyzer.valveV17.setState(false)

                txContext.recordAction(object : ReversibleAction {
                    override val id = "valve_v20_reset_2"
                    override suspend fun reverse() {
                        analyzer.valveV20.setState(true)
                    }
                })

                txContext.recordAction(object : ReversibleAction {
                    override val id = "valve_v17_reset_2"
                    override suspend fun reverse() {
                        analyzer.valveV17.setState(true)
                    }
                })

                throw RuntimeException("Transaction test exception")
            }
        } catch (e: RuntimeException) {
        }

        assertTrue(analyzer.valveV20.getState(), "V20 should be open after transaction rollback")
        assertTrue(analyzer.valveV17.getState(), "V17 should be open after transaction rollback")
    }

    @Test
    fun `test analyzer recipe execution with lifecycle checks`() = runTest {
        val context = createTestContext()
        val analyzer = AnalyzerDevice(context)

        analyzer.initChildren()

        assertEquals(LifecycleState.INITIAL, analyzer.lifecycleState, "Analyzer should be in INITIAL state")

        analyzer.start()
        assertEquals(LifecycleState.STARTED, analyzer.lifecycleState, "Analyzer should be in STARTED state")

        analyzer.executeRecipe1()

        assertEquals(0.0, analyzer.needleDevice.getPosition(), "Needle should return to position 0")

        analyzer.stop()
        assertEquals(LifecycleState.STOPPED, analyzer.lifecycleState, "Analyzer should be in STOPPED state")
    }

    @Test
    fun `test build DeviceManagerConfig`() {
        val config = DeviceManagerConfig(
            messageBufferSize = 500,
            defaultConcurrencyLevel = 2,
            defaultStartTimeout = 60.seconds,
            defaultStopTimeout = 5.seconds
        )

        assertEquals(500, config.messageBufferSize)
        assertEquals(2, config.defaultConcurrencyLevel)
        assertEquals(60.seconds, config.defaultStartTimeout)
        assertEquals(5.seconds, config.defaultStopTimeout)
    }

    @Test
    fun `test build with invalid buffer size`() {
        assertFailsWith<IllegalArgumentException> {
            DeviceManagerConfig(messageBufferSize = 0)
        }
    }

    class MagixMessageBusStub : MessageBus {
        override fun subscribe(filter: DeviceMessageFilter) = flowOf<DeviceMessage>()
        override suspend fun publish(message: DeviceMessage) { /* no-op */ }
        override fun close() { /* no-op */ }
    }
}
