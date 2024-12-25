package space.kscience.controls.spec

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.meta.Meta
import space.kscience.dataforge.meta.double
import space.kscience.dataforge.meta.int
import space.kscience.dataforge.names.parseAsName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompositeControlTest {

    // ---------------------- Device Specifications ----------------------------------

    public object StepperMotorSpec : CompositeControlComponentSpec<StepperMotorDevice>() {
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

    public object ValveSpec : CompositeControlComponentSpec<ValveDevice>() {
        public val state by booleanProperty(
            read = { getState() },
            write = { _, value -> setState(value) }
        )
    }

    public object PressureChamberSpec : CompositeControlComponentSpec<PressureChamberDevice>() {
        public val pressure by doubleProperty(
            read = { getPressure() },
            write = { _, value -> setPressure(value) }
        )
    }

    public object SyringePumpSpec : CompositeControlComponentSpec<SyringePumpDevice>() {
        public val volume by doubleProperty(
            read = { getVolume() },
            write = { _, value -> setVolume(value) }
        )
    }

    public object ReagentSensorSpec : CompositeControlComponentSpec<ReagentSensorDevice>() {
        public val isPresent by booleanProperty(
            read = { checkReagent() }
        )
    }

    public object NeedleSpec : CompositeControlComponentSpec<NeedleDevice>() {
        public val mode by enumProperty<NeedleDevice.Mode, NeedleDevice>(
            read = { getMode() },
            write = { _, value -> setMode(value) }
        )

        public val position by doubleProperty(
            read = { getPosition() },
            write = { _, value -> setPosition(value) }
        )
    }

    public object ShakerSpec : CompositeControlComponentSpec<ShakerDevice>() {
        public val verticalMotor by childSpec<StepperMotorSpec, StepperMotorDevice>(StepperMotorSpec)
        public val horizontalMotor by childSpec<StepperMotorSpec, StepperMotorDevice>(StepperMotorSpec)
    }

    public object TransportationSystemSpec : CompositeControlComponentSpec<TransportationSystem>() {
        public val slideMotor by childSpec<StepperMotorSpec, StepperMotorDevice>(StepperMotorSpec)
        public val pushMotor by childSpec<StepperMotorSpec, StepperMotorDevice>(StepperMotorSpec)
        public val receiveMotor by childSpec<StepperMotorSpec, StepperMotorDevice>(StepperMotorSpec)
    }


    public object AnalyzerSpec : CompositeControlComponentSpec<AnalyzerDevice>() {
        public val transportationSystem by childSpec<TransportationSystemSpec, TransportationSystem>(TransportationSystemSpec)
        public val shakerDevice by childSpec<ShakerSpec, ShakerDevice>(ShakerSpec)
        public val needleDevice by childSpec<NeedleSpec, NeedleDevice>(NeedleSpec)

        public val valveV20 by childSpec<ValveSpec, ValveDevice>(ValveSpec)
        public val valveV17 by childSpec<ValveSpec, ValveDevice>(ValveSpec)
        public val valveV18 by childSpec<ValveSpec, ValveDevice>(ValveSpec)
        public val valveV35 by childSpec<ValveSpec, ValveDevice>(ValveSpec)

        public val pressureChamberHigh by childSpec<PressureChamberSpec, PressureChamberDevice>(PressureChamberSpec)
        public val pressureChamberLow by childSpec<PressureChamberSpec, PressureChamberDevice>(PressureChamberSpec)

        public val syringePumpMA100 by childSpec<SyringePumpSpec, SyringePumpDevice>(SyringePumpSpec)
        public val syringePumpMA25 by childSpec<SyringePumpSpec, SyringePumpDevice>(SyringePumpSpec)

        public val reagentSensor1 by childSpec<ReagentSensorSpec, ReagentSensorDevice>(ReagentSensorSpec)
        public val reagentSensor2 by childSpec<ReagentSensorSpec, ReagentSensorDevice>(ReagentSensorSpec)
        public val reagentSensor3 by childSpec<ReagentSensorSpec, ReagentSensorDevice>(ReagentSensorSpec)
    }

    // ---------------------- Device Implementations ----------------------------------

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
                println("StepperMotorDevice: Moving to position $_position")
                delay(100)
            } else {
                println("StepperMotorDevice: Invalid position $value (max: $maxPosition)")
            }
        }

    }

    // Implementation of Valve Device
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
            println("ValveDevice: Valve is now $stateStr")
            delay(50)
        }

        /**
         * Simulates clicking the valve.
         */
        public suspend fun click() {
            println("ValveDevice: Clicking valve...")
            setState(true)
            delay(50)
            setState(false)
            println("ValveDevice: Valve click completed")
        }

    }

    // Implementation of Pressure Chamber Device
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
            println("PressureChamberDevice: Pressure is now $_pressure")
            delay(50)
        }

    }

    // Implementation of Syringe Pump Device
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
                println("SyringePumpDevice: Volume is now $_volume ml")
                delay(100)
            } else {
                println("SyringePumpDevice: Invalid volume $value (max: $maxVolume)")
            }
        }

    }

    // Implementation of Reagent Sensor Device
    public class ReagentSensorDevice(
        context: Context,
        meta: Meta = Meta.EMPTY
    ) : ConfigurableCompositeControlComponent<ReagentSensorDevice>(ReagentSensorSpec, context, meta) {

        /**
         * Checks for reagent presence.
         * @return true if reagent is present.
         */
        public suspend fun checkReagent(): Boolean {
            println("ReagentSensorDevice: Checking for reagent presence...")
            delay(100) // Simulate detection time
            val isPresent = true // Assume reagent is present
            println("ReagentSensorDevice: Reagent is ${if (isPresent) "present" else "not present"}")
            return isPresent
        }

    }

    // Implementation of Needle Device
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
            println("NeedleDevice: Mode is now $_mode")
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
                println("NeedleDevice: Moved to position $_position mm")
                delay(100)
            } else {
                println("NeedleDevice: Invalid position $value mm")
            }
        }

        /**
         * Executes washing process for given duration
         * @param duration time for washing in seconds
         */
        public suspend fun performWashing(duration: Int) {
            println("NeedleDevice: Washing in progress for $duration seconds")
            delay(duration * 1000L) // Simulate washing (1 second = 1000 ms)
        }

        /**
         * Execute sampling procedure
         */
        public suspend fun performSampling() {
            println("NeedleDevice: Performing sample intake at position $_position mm")
            delay(500) // Simulate sampling time
        }

    }

    // Implementation of Shaker Device
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
            println("ShakerDevice: Shaking started, cycles: $cycles")
            repeat(cycles) {
                verticalMotor.setPosition(3)
                verticalMotor.setPosition(1)
                println("ShakerDevice: cycle ${it+1} done")
            }
            println("ShakerDevice: Shaking completed")
        }
    }

    // Implementation of Transportation System
    public class TransportationSystem(
        context: Context,
        meta: Meta = Meta.EMPTY
    ) : ConfigurableCompositeControlComponent<TransportationSystem>(TransportationSystemSpec, context, meta) {

        /**
         * Get slide stepper motor
         */
        public val slideMotor by childDevice<StepperMotorDevice>()


        /**
         * Get push stepper motor
         */
        public val pushMotor by childDevice<StepperMotorDevice>()

        /**
         * Get receive stepper motor
         */
        public val receiveMotor by childDevice<StepperMotorDevice>()

    }

    // Implementation of Analyzer Device
    public class AnalyzerDevice(
        context: Context,
        meta: Meta = Meta.EMPTY
    ) : ConfigurableCompositeControlComponent<AnalyzerDevice>(AnalyzerSpec, context, meta) {

        /**
         * Get transportation system
         */
        public val transportationSystem by childDevice<TransportationSystem>()

        /**
         * Get shaker device
         */
        public val shakerDevice by childDevice<ShakerDevice>()

        /**
         * Get needle device
         */
        public val needleDevice by childDevice<NeedleDevice>()

        /**
         * Get valve V20
         */
        public val valveV20 by childDevice<ValveDevice>()

        /**
         * Get valve V17
         */
        public val valveV17 by childDevice<ValveDevice>()

        /**
         * Get valve V18
         */
        public val valveV18 by childDevice<ValveDevice>()

        /**
         * Get valve V35
         */
        public val valveV35 by childDevice<ValveDevice>()

        /**
         * Get high pressure chamber
         */
        public val pressureChamberHigh by childDevice<PressureChamberDevice>()

        /**
         * Get low pressure chamber
         */
        public val pressureChamberLow by childDevice<PressureChamberDevice>()

        /**
         * Get syringe pump MA100
         */
        public val syringePumpMA100 by childDevice<SyringePumpDevice>()

        /**
         * Get syringe pump MA25
         */
        public val syringePumpMA25 by childDevice<SyringePumpDevice>()

        /**
         * Get reagent sensor 1
         */
        public val reagentSensor1 by childDevice<ReagentSensorDevice>()

        /**
         * Get reagent sensor 2
         */
        public val reagentSensor2 by childDevice<ReagentSensorDevice>()

        /**
         * Get reagent sensor 3
         */
        public val reagentSensor3 by childDevice<ReagentSensorDevice>()

        /**
         * Simulates a process of taking sample from tubes.
         */
        public suspend fun processSample() {
            println("The beginning of the sampling process")

            // Step 1: Open valve V20 and start taking a sample with a syringe pump MA 100 mcl
            valveV20.setState(true)
            syringePumpMA100.setVolume(0.1)
            delay(500)  // Simulating waiting time for liquid collection
            valveV20.setState(false)

            // Step 2: Open valve V17 and start delivering lysis buffer with syringe pump MA 2.5 ml
            valveV17.setState(true)
            syringePumpMA25.setVolume(2.5)
            delay(500)  // Simulate lysis buffer delivery time
            valveV17.setState(false)

            // Step 3: Cleaning system
            syringePumpMA100.setVolume(0.0)
            syringePumpMA25.setVolume(0.0)

            println("The sampling process is completed")
        }


        /**
         * Simulates the analyzer calibration procedure.
         */
        public suspend fun calibrate() {
            println("The beginning of calibration...")

            // Step 1: Calibrate positions of all motors
            val motors = listOf(
                transportationSystem.slideMotor,
                transportationSystem.pushMotor,
                transportationSystem.receiveMotor,
                shakerDevice.verticalMotor,
                shakerDevice.horizontalMotor,

                )

            for (motor in motors) {
                for (position in 0..motor.maxPosition) {
                    motor.setPosition(position)
                }
                motor.setPosition(0)
            }

            // Step 2: Click all valves and set them to zero position
            val valves = listOf(valveV20, valveV17, valveV18, valveV35)
            for (valve in valves) {
                valve.click()
                valve.setState(false)
            }

            // Step 3: Pump up pressure in high pressure chamber
            pressureChamberHigh.setPressure(2.0)
            // Step 4: Pump out pressure from low pressure chamber
            pressureChamberLow.setPressure(-1.0)

            // Step 5: Fill the hydraulic system
            // 5.1 Check if reagents are present
            val sensors = listOf(reagentSensor1, reagentSensor2, reagentSensor3)
            for (sensor in sensors) {
                sensor.checkReagent()
            }

            // 5.2 Perform 5 times full pump movement with all syringe pumps
            val pumps = listOf(syringePumpMA100, syringePumpMA25)
            for (pump in pumps) {
                repeat(5) {
                    pump.setVolume(pump.maxVolume)
                    pump.setVolume(0.0)
                }
            }

            // 5.3 Wash needle at its washing position
            needleDevice.setPosition(0.0)
            needleDevice.setMode(NeedleDevice.Mode.WASHING)
            needleDevice.performWashing(5)

            println("Calibration is completed")
        }

        /**
         * Execute recipe 1 - sample tube deliver
         */
        public suspend fun executeRecipe1() {
            println("Executing recipe 1")

            // Step 1: Move a slide to the next position
            val currentSlidePosition = transportationSystem.slideMotor.getPosition()
            transportationSystem.slideMotor.setPosition(currentSlidePosition + 1)
            println("Moved a slide to position ${currentSlidePosition+1}")

            // Step 2: Capture a tube for mixing
            println("Capturing tube for mixing")

            // 2.1 - 2.10: Control over a shaker and motors
            shakerDevice.verticalMotor.setPosition(1)
            shakerDevice.horizontalMotor.setPosition(1)
            println("Shaker: vertical - 1, horizontal - 1")

            shakerDevice.horizontalMotor.setPosition(2)
            println("Shaker: horizontal - 2")

            shakerDevice.verticalMotor.setPosition(2)
            println("Shaker: vertical - 2")

            // Shake
            shakerDevice.shake(5)
            println("Shaker: movement done")

            // Step 3: Sampling and measurement
            executeSampling()
            needleDevice.setPosition(0.0)
            println("Needle moved to its initial position")

        }

        /**
         * Execute recipe 2 - Automatic Measurement
         */
        public suspend fun executeRecipe2() {
            println("Executing Recipe 2 - Automatic Measurement")

            transportationSystem.receiveMotor.setPosition(transportationSystem.receiveMotor.getPosition() + 1)
            println("Pusher moved to position ${transportationSystem.receiveMotor.getPosition() + 1}")

            //Check for a tray, if missing move again
            if (!checkTrayInPushSystem()) {
                println("Tray missing. Trying to move again")
                transportationSystem.receiveMotor.setPosition(transportationSystem.receiveMotor.getPosition() + 1)
            } else {
                executeSampling()
            }

            // If last position, reset the plate
            if (transportationSystem.receiveMotor.getPosition() >= transportationSystem.receiveMotor.maxPosition) {
                println("Plate is complete. Resetting pusher to initial position")
                transportationSystem.receiveMotor.setPosition(0)
            }

            println("Recipe 2 execution finished")
            needleDevice.setPosition(0.0)
            println("Needle moved to its initial position")
        }

        /**
         * Execute recipe 3 - Single Measurement
         */
        public suspend fun executeRecipe3() {
            println("Executing Recipe 3 - Single measurement")
            executeSampling()
            println("Recipe 3 completed")
            needleDevice.setPosition(0.0)
            println("Needle moved to its initial position")
        }


        /**
         * Simulates tray presence check
         */
        private suspend fun checkTrayInPushSystem(): Boolean {
            println("Checking for a tray in a pushing system")
            delay(200)
            return true // Simulate tray presence
        }

        /**
         * Function to execute sampling process with the needle
         */
        private suspend fun executeSampling() {
            needleDevice.setMode(NeedleDevice.Mode.SAMPLING)
            needleDevice.performSampling()
            needleDevice.setMode(NeedleDevice.Mode.WASHING)
            needleDevice.performWashing(2)
        }
    }

    private fun createTestContext() = Context("test")

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

        motor.setPosition(200) //Should be outside the range, so not changed
        assertEquals(0, motor.getPosition(), "Position should not be set for invalid value")
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
        assertFalse(valve.getState(), "Valve should be closed after click")
    }

    @Test
    fun `test PressureChamberDevice pressure setting`() = runTest {
        val context = createTestContext()
        val chamber = PressureChamberDevice(context)

        chamber.setPressure(1.5)
        assertEquals(1.5, chamber.getPressure(), "Pressure should be set correctly")

        chamber.setPressure(0.0)
        assertEquals(0.0, chamber.getPressure(), "Pressure should be set to 0")

        chamber.setPressure(-1.0)
        assertEquals(-1.0, chamber.getPressure(), "Pressure should be set to negative value")
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
        assertEquals(0.0, pump.getVolume(), "Pump volume should not be set for invalid value")
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

        //Test setting mode
        needle.setMode(NeedleDevice.Mode.SAMPLING)
        assertEquals(NeedleDevice.Mode.SAMPLING, needle.getMode(), "Mode should be set to SAMPLING")

        needle.setMode(NeedleDevice.Mode.WASHING)
        assertEquals(NeedleDevice.Mode.WASHING, needle.getMode(), "Mode should be set to WASHING")

        //Test setting position
        needle.setPosition(50.0)
        assertEquals(50.0, needle.getPosition(), "Position should be set correctly")

        needle.setPosition(0.0)
        assertEquals(0.0, needle.getPosition(), "Position should be set to 0")

        needle.setPosition(100.0)
        assertEquals(100.0, needle.getPosition(), "Position should be set to max")
    }


    @Test
    fun `test NeedleDevice invalid position`() = runTest {
        val context = createTestContext()
        val needle = NeedleDevice(context)

        needle.setPosition(200.0)
        assertEquals(0.0, needle.getPosition(), "Needle position should not be set for invalid value")

    }


    @Test
    fun `test ShakerDevice shaking`() = runTest {
        val context = createTestContext()
        val shaker = ShakerDevice(context)

        shaker.initChildren()
        shaker.start()// Start the device

        // Access properties to initialize motors and test shaking
        val verticalMotor = shaker.verticalMotor
        val horizontalMotor = shaker.horizontalMotor

        shaker.shake(2)
        val verticalMotorPosition = verticalMotor.getPosition()
        val horizontalMotorPosition = horizontalMotor.getPosition()

        assertEquals(2,verticalMotorPosition, "Vertical motor position should be set to 2 after shaking")
        assertEquals(1,horizontalMotorPosition, "Horizontal motor position should be set to 1 after shaking")
    }

    @Test
    fun `test TransportationSystem motors existence`() = runTest {
        val context = createTestContext()
        val transportationSystem = TransportationSystem(context)

        transportationSystem.initChildren()
        transportationSystem.start()// Start the device

        // Access properties to initialize motors and test existence
        assertNotNull(transportationSystem.slideMotor, "slideMotor should exist")
        assertNotNull(transportationSystem.pushMotor, "pushMotor should exist")
        assertNotNull(transportationSystem.receiveMotor, "receiveMotor should exist")
    }

    @Test
    fun `test AnalyzerDevice device access`() = runTest {
        val context = createTestContext()
        val analyzer = AnalyzerDevice(context)
        analyzer.initChildren()
        analyzer.start()// Start the device

        // Access properties to initialize child devices
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
}