// LLM generated code for Wet Gas Cleaning Plant Demonstrator Tests
package space.kscience.controls.demo.constructor

import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.context.Context
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WetGasCleaningPlantTest {

    private fun createContext(): Context = Context {
        plugin(DeviceManager)
        plugin(ClockManager)
    }

    @Test
    fun testNominalRegimeBalancesAndKpi() {
        val context = createContext()
        val model = WetGasCleaningPlant(context)

        // Verify initial nominal parameters
        assertEquals(188.0, model.inletGasFlowRate.value)
        assertEquals(18.0, model.inletSo2Concentration.value)
        assertEquals(0.15, model.inletSo3Fraction.value)
        assertEquals(58.5, model.dilutionAirFlowRate.value)
        assertEquals(5.89, model.makeUpWaterFlowRate.value)
        assertEquals(WespOperationalState.BOTH_ACTIVE, model.wespOperationalState.value)

        // Verify nominal gas inlet rate (~52.22 kg/s)
        val expectedInletKgPerSec = 188.0 * 1000.0 / 3600.0
        assertEquals(expectedInletKgPerSec, model.gasInletProduction.value.value, 0.01)

        // Verify delivered SO2 % at nominal mode (~12.0%)
        val deliveredSo2 = model.deliveredSo2Percent.value
        assertTrue(deliveredSo2 in 11.5..12.5, "Expected delivered SO2 ~12%, got $deliveredSo2%")

        // Verify outlet H2SO4 mist is below statutory threshold (≤ 5.0 mg/Nm3)
        val outletMist = model.outletMistConcentration.value
        assertTrue(outletMist <= 5.0, "Expected compliant outlet mist ≤ 5 mg/Nm3, got $outletMist mg/Nm3")
        assertTrue(outletMist in 1.0..2.0, "Expected nominal outlet mist ~1.48 mg/Nm3, got $outletMist mg/Nm3")
    }

    @Test
    fun testPerturbationAndWespRegimes() {
        val context = createContext()
        val model = WetGasCleaningPlant(context)

        // Test Scenario SC-02: Partial Load Turndown (50% gas throughput)
        model.inletGasFlowRate.value = 94.0
        assertEquals(94.0 * 1000.0 / 3600.0, model.gasInletProduction.value.value, 0.01)

        // Test Scenario SC-05: WESP Tripped / Off
        model.wespOperationalState.value = WespOperationalState.TRIPPED
        val trippedMist = model.outletMistConcentration.value
        assertTrue(trippedMist > 50.0, "Expected elevated mist when WESP is tripped, got $trippedMist mg/Nm3")

        // Test WESP Washing Mode at nominal load (188 t/h)
        model.inletGasFlowRate.value = 188.0
        model.wespOperationalState.value = WespOperationalState.WASHING_MODE
        val washingMist = model.outletMistConcentration.value
        assertTrue(washingMist in 5.0..10.0, "Expected washing mode mist ~6.6 mg/Nm3, got $washingMist mg/Nm3")
    }
}
