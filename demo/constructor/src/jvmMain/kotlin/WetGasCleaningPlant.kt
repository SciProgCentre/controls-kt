// LLM generated code for Wet Gas Cleaning Plant Demonstrator
package space.kscience.controls.demo.constructor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import space.kscience.controls.compose.letsplot.PlotNumberState
import space.kscience.controls.compose.letsplot.PlotNumericState
import space.kscience.controls.compose.letsplot.TimeSeriesPlot
import space.kscience.controls.constructor.*
import space.kscience.controls.constructor.units.*
import space.kscience.controls.manager.DeviceManager
import space.kscience.controls.models.continuous.*
import space.kscience.controls.time.ClockManager
import space.kscience.dataforge.context.Context
import java.awt.Dimension
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Operational states for the two-stage Wet Electrostatic Precipitators (WESP B-03 / B-04).
 */
public enum class WespOperationalState(
    public val title: String,
    public val stage1Efficiency: Double,
    public val stage2Efficiency: Double,
) {
    BOTH_ACTIVE("Both Active (99.75%)", 0.95, 0.95),
    STAGE_1_ONLY("Stage 1 Only (95%)", 0.95, 0.0),
    STAGE_2_ONLY("Stage 2 Only (95%)", 0.0, 0.95),
    WASHING_MODE("Washing Mode (98.9%)", 0.95, 0.78),
    TRIPPED("Tripped / Off (0%)", 0.0, 0.0);

    public val totalEfficiency: Double
        get() = 1.0 - (1.0 - stage1Efficiency) * (1.0 - stage2Efficiency)
}

/**
 * Reactive continuous flow simulation model representing the Wet Gas Cleaning Plant
 * (Отделение мокрой очистки газа — МОГ, ЦПиНСК НМЗ).
 */
public class WetGasCleaningPlant(
    context: Context,
) : ContinuousFlowModel(context) {

    // --- 1. Key Adjustable Input States (Runtime Parameters) ---
    public val inletGasFlowRate: MutableValueState<Double> = MutableValueState(188.0) // t/h (nom. 188.0)
    public val inletSo2Concentration: MutableValueState<Double> = MutableValueState(18.0) // % vol dry (nom. 18.0%)
    public val inletSo3Fraction: MutableValueState<Double> = MutableValueState(0.15) // % vol dry (nom. 0.15%)
    public val inletDustConcentration: MutableValueState<Double> = MutableValueState(0.20) // g/Nm3 (nom. 0.20)
    public val dilutionAirFlowRate: MutableValueState<Double> = MutableValueState(58.5) // kNm3/h (nom. 58.5)
    public val makeUpWaterFlowRate: MutableValueState<Double> = MutableValueState(5.89) // t/h (nom. 5.89)
    public val condenserExitTemp: MutableValueState<Double> = MutableValueState(40.0) // °C (nom. 40.0)
    public val wespOperationalState: MutableValueState<WespOperationalState> =
        MutableValueState(WespOperationalState.BOTH_ACTIVE)

    // Equipment availability toggles
    public val p01RecirculationPumpActive: MutableValueState<Boolean> = MutableValueState(true)
    public val p03RecirculationPumpActive: MutableValueState<Boolean> = MutableValueState(true)

    // --- 2. Gas Ingress & Utilities Producers ---
    public val gasInletProduction: ValueState<AmountPerSecond<Kilograms>> = inletGasFlowRate.map {
        AmountPerSecond(it * 1000.0 / 3600.0)
    }
    public val gasInlet: ContinuousProducer<Kilograms, NumericAmount<Kilograms>> =
        producer(Kilograms, gasInletProduction)

    public val dilutionAirProduction: ValueState<AmountPerSecond<Kilograms>> = dilutionAirFlowRate.map {
        AmountPerSecond(it * 1000.0 * 1.293 / 3600.0)
    }
    public val dilutionAir: ContinuousProducer<Kilograms, NumericAmount<Kilograms>> =
        producer(Kilograms, dilutionAirProduction)

    public val makeUpWaterProduction: ValueState<AmountPerSecond<Kilograms>> = makeUpWaterFlowRate.map {
        AmountPerSecond(it * 1000.0 / 3600.0)
    }
    public val makeUpWater: ContinuousProducer<Kilograms, NumericAmount<Kilograms>> =
        producer(Kilograms, makeUpWaterProduction)

    // --- 3. Stage 1 Quench & Recirculation (B-01, T-01) ---
    public val quenchTower: ContinuousSeparate<Kilograms, NumericAmount<Kilograms>> = separator(
        algebra = Kilograms,
        separationRule = SeparationRule.proportional(Kilograms, mapOf("gas" to 0.96, "drain" to 0.04))
    ).apply {
        connectProducer(gasInlet)
    }

    public val stage1Mixer: ContinuousMix<Kilograms, NumericAmount<Kilograms>> = mix(
        algebra = Kilograms,
        supplyKeys = setOf("makeUpWater", "quenchDrain", "cascade")
    ).apply {
        connectProducer("makeUpWater", makeUpWater)
        connectProducer("quenchDrain", quenchTower.asProducer("drain").delayed(this@WetGasCleaningPlant, 200.milliseconds))
    }

    public val stage1AcidTank: ContinuousBuffer<Kilograms, NumericAmount<Kilograms>> = buffer(
        algebra = Kilograms,
        bufferCapacity = NumericAmount(80_000.0), // 80 m3 (~80,000 kg)
        initialLevel = NumericAmount(42_000.0)
    ).apply {
        connectProducer(stage1Mixer)
    }

    // --- 4. Stage 2 Condenser & Cooling (B-02, T-03) ---
    public val condenserTower: ContinuousSeparate<Kilograms, NumericAmount<Kilograms>> = separator(
        algebra = Kilograms,
        separationRule = SeparationRule.proportional(Kilograms, mapOf("gas" to 0.90, "condensate" to 0.10))
    ).apply {
        connectProducer(quenchTower.asProducer("gas"))
    }

    // --- 5. Two-Stage WESP Mist Precipitators (B-03, B-04) ---
    public val wespStage1: ContinuousSeparate<Kilograms, NumericAmount<Kilograms>> = separator(
        algebra = Kilograms,
        separationRule = SeparationRule.proportional(Kilograms, mapOf("gas" to 0.998, "drain" to 0.002))
    ).apply {
        connectProducer(condenserTower.asProducer("gas"))
    }

    public val wespStage2: ContinuousSeparate<Kilograms, NumericAmount<Kilograms>> = separator(
        algebra = Kilograms,
        separationRule = SeparationRule.proportional(Kilograms, mapOf("gas" to 0.999, "drain" to 0.001))
    ).apply {
        connectProducer(wespStage1.asProducer("gas"))
    }

    public val stage2Mixer: ContinuousMix<Kilograms, NumericAmount<Kilograms>> = mix(
        algebra = Kilograms,
        supplyKeys = setOf("condensate", "wespDrain1", "wespDrain2")
    ).apply {
        connectProducer("condensate", condenserTower.asProducer("condensate").delayed(this@WetGasCleaningPlant, 200.milliseconds))
        connectProducer("wespDrain1", wespStage1.asProducer("drain").delayed(this@WetGasCleaningPlant, 200.milliseconds))
        connectProducer("wespDrain2", wespStage2.asProducer("drain").delayed(this@WetGasCleaningPlant, 200.milliseconds))
    }

    public val stage2AcidTank: ContinuousBuffer<Kilograms, NumericAmount<Kilograms>> = buffer(
        algebra = Kilograms,
        bufferCapacity = NumericAmount(60_000.0), // 60 m3 (~60,000 kg)
        initialLevel = NumericAmount(32_000.0)
    ).apply {
        connectProducer(stage2Mixer)
    }

    // --- 6. Weak Acid Stripping & Neutralization (B-08, T-02, SNK-02) ---
    public val so2Stripper: ContinuousSeparate<Kilograms, NumericAmount<Kilograms>> = separator(
        algebra = Kilograms,
        separationRule = SeparationRule.proportional(Kilograms, mapOf("acid" to 0.98, "gas" to 0.02))
    ).apply {
        connectProducer(stage1AcidTank)
    }

    public val weakAcidTank: ContinuousBuffer<Kilograms, NumericAmount<Kilograms>> = buffer(
        algebra = Kilograms,
        bufferCapacity = NumericAmount(50_000.0), // 50 m3 (~50,000 kg)
        initialLevel = NumericAmount(25_000.0)
    ).apply {
        connectProducer(so2Stripper.asProducer("acid"))
    }

    public val neutralizationSink: ContinuousConsumer<Kilograms, NumericAmount<Kilograms>> = consumer(
        algebra = Kilograms,
        capacity = AmountPerSecond(2.14) // nom. ~7.7 t/h
    ).apply {
        connectProducer(weakAcidTank)
    }

    // --- 7. Gas Mixing & Final Delivery (B-05, SNK-01) ---
    public val gasMixer: ContinuousMix<Kilograms, NumericAmount<Kilograms>> = mix(
        algebra = Kilograms,
        supplyKeys = setOf("cleanedGas", "dilutionAir")
    ).apply {
        connectProducer("cleanedGas", wespStage2.asProducer("gas").delayed(this@WetGasCleaningPlant, 200.milliseconds))
        connectProducer("dilutionAir", dilutionAir)
    }

    public val dryingTowerSink: ContinuousConsumer<Kilograms, NumericAmount<Kilograms>> = consumer(
        algebra = Kilograms,
        capacity = AmountPerSecond(120.0)
    ).apply {
        connectProducer(gasMixer)
    }

    init {
        // Wire Stage 2 to Stage 1 cascade overflow (L-13) with delay to break synchronous feedback loops
        stage1Mixer.connectProducer("cascade", stage2AcidTank.delayed(this, 200.milliseconds))
    }

    // --- 8. Dynamic KPI Calculations ---

    /**
     * Delivered SO2 concentration (%) after B-05 gas mixing.
     */
    public val deliveredSo2Percent: ValueState<Double> = combineState(
        first = inletGasFlowRate,
        second = inletSo2Concentration,
        third = dilutionAirFlowRate,
        transformation = { gasRate: Double, so2InletPct: Double, airFlow: Double ->
            val dryGasInflow = (gasRate * 0.94) / 1.517 // kNm3/h dry gas equivalent
            val so2Volume = dryGasInflow * (so2InletPct / 100.0)
            val totalDryGas = dryGasInflow + airFlow
            if (totalDryGas > 0.0) (so2Volume / totalDryGas) * 100.0 else 0.0
        }
    )

    /**
     * Outlet H2SO4 mist concentration (mg/Nm3) downstream of WESP Stage 2.
     */
    public val outletMistConcentration: ValueState<Double> = combineState(
        first = inletGasFlowRate,
        second = inletSo3Fraction,
        third = wespOperationalState,
        transformation = { gasRate: Double, so3Frac: Double, state: WespOperationalState ->
            val so3Factor = so3Frac / 0.15
            val baseMist = 590.0 * so3Factor
            val flowRatio = (gasRate / 188.0).coerceAtLeast(0.1)

            // Exponential Deutsch efficiency degradation under elevated gas velocity
            val stage1Penetration = (1.0 - state.stage1Efficiency).pow(1.0 / flowRatio)
            val stage2Penetration = (1.0 - state.stage2Efficiency).pow(1.0 / flowRatio)

            val mist = baseMist * stage1Penetration * stage2Penetration
            mist.coerceAtLeast(0.1)
        }
    )

    /**
     * Weak acid bleed flow rate (t/h) discharged from T-01 through B-08 into neutralization.
     */
    public val weakAcidBleedRate: ValueState<Double> = so2Stripper.asProducer("acid").production.map {
        it.value * 3.6
    }

    /**
     * Cleaned gas mass flow delivered to drying section (t/h).
     */
    public val deliveredGasFlowRate: ValueState<Double> = dryingTowerSink.consumation.map {
        it.value * 3.6
    }
}

@OptIn(ExperimentalSplitPaneApi::class)
public fun main() {
    val context = Context {
        plugin(DeviceManager)
        plugin(ClockManager)
    }

    val model = WetGasCleaningPlant(context)
    val maxAge = 60.seconds

    application {
        Window(
            title = "Wet Gas Cleaning Plant Digital Twin (МОГ ЦПиНСК НМЗ)",
            onCloseRequest = ::exitApplication
        ) {
            window.minimumSize = Dimension(1200, 800)

            MaterialTheme {
                HorizontalSplitPane {
                    // --- Left Panel: Operator Controls & KPI Cards (35% Width) ---
                    first(420.dp) {
                        Column(
                            modifier = Modifier
                                .background(Color(0xFFF3F4F6))
                                .fillMaxHeight()
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "OPERATOR CONTROLS (МОГ)",
                                style = MaterialTheme.typography.h6.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color(0xFF1E3A8A)
                                )
                            )

                            // Section 1: Gas Ingress & Process Controls
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = 3.dp,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("1. Process Feed Parameters", fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2937))

                                    // 1. Gas Flow Rate
                                    val gasRate by model.inletGasFlowRate.subscribe().collectAsState(188.0)
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Gas Feed Rate (Q_gas):", fontSize = 13.sp)
                                            Text("${gasRate.roundToInt()} t/h", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Slider(
                                            value = gasRate.toFloat(),
                                            onValueChange = { model.inletGasFlowRate.value = it.toDouble() },
                                            valueRange = 50.0f..250.0f,
                                            steps = 199
                                        )
                                    }

                                    // 2. SO2 Concentration
                                    val so2In by model.inletSo2Concentration.subscribe().collectAsState(18.0)
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Inlet SO2 Concentration:", fontSize = 13.sp)
                                            Text("${"%.1f".format(so2In)} %", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Slider(
                                            value = so2In.toFloat(),
                                            onValueChange = { model.inletSo2Concentration.value = it.toDouble() },
                                            valueRange = 8.0f..28.0f,
                                            steps = 39
                                        )
                                    }

                                    // 3. SO3 Fraction
                                    val so3In by model.inletSo3Fraction.subscribe().collectAsState(0.15)
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Inlet SO3 Precursor:", fontSize = 13.sp)
                                            Text("${"%.2f".format(so3In)} %", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Slider(
                                            value = so3In.toFloat(),
                                            onValueChange = { model.inletSo3Fraction.value = it.toDouble() },
                                            valueRange = 0.05f..0.50f,
                                            steps = 44
                                        )
                                    }

                                    // 4. Dust Loading
                                    val dustIn by model.inletDustConcentration.subscribe().collectAsState(0.20)
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Inlet Dust Load:", fontSize = 13.sp)
                                            Text("${"%.2f".format(dustIn)} g/Nm³", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Slider(
                                            value = dustIn.toFloat(),
                                            onValueChange = { model.inletDustConcentration.value = it.toDouble() },
                                            valueRange = 0.05f..1.00f,
                                            steps = 18
                                        )
                                    }
                                }
                            }

                            // Section 2: Dilution & Cooling Utilities
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = 3.dp,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("2. Utilities & Temperature", fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2937))

                                    // 5. Dilution Air Flow Rate
                                    val airFlow by model.dilutionAirFlowRate.subscribe().collectAsState(58.5)
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Dilution Air (B-05):", fontSize = 13.sp)
                                            Text("${"%.1f".format(airFlow)} kNm³/h", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Slider(
                                            value = airFlow.toFloat(),
                                            onValueChange = { model.dilutionAirFlowRate.value = it.toDouble() },
                                            valueRange = 0.0f..100.0f,
                                            steps = 99
                                        )
                                    }

                                    // 6. Make-Up Water Inflow
                                    val waterIn by model.makeUpWaterFlowRate.subscribe().collectAsState(5.89)
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Make-Up Water (W-01):", fontSize = 13.sp)
                                            Text("${"%.2f".format(waterIn)} t/h", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Slider(
                                            value = waterIn.toFloat(),
                                            onValueChange = { model.makeUpWaterFlowRate.value = it.toDouble() },
                                            valueRange = 0.0f..15.0f,
                                            steps = 149
                                        )
                                    }

                                    // 7. Condenser Outlet Temperature
                                    val condTemp by model.condenserExitTemp.subscribe().collectAsState(40.0)
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Condenser T_out (B-02):", fontSize = 13.sp)
                                            Text("${"%.1f".format(condTemp)} °C", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Slider(
                                            value = condTemp.toFloat(),
                                            onValueChange = { model.condenserExitTemp.value = it.toDouble() },
                                            valueRange = 30.0f..65.0f,
                                            steps = 69
                                        )
                                    }
                                }
                            }

                            // Section 3: Equipment & WESP Field States
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = 3.dp,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("3. Equipment & WESP Status", fontWeight = FontWeight.SemiBold, color = Color(0xFF1F2937))

                                    // 8. WESP State Selector
                                    val currentWespState by model.wespOperationalState.subscribe().collectAsState(WespOperationalState.BOTH_ACTIVE)
                                    Text("8. WESP Operational Regime:", fontSize = 13.sp)
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        WespOperationalState.entries.forEach { state ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                RadioButton(
                                                    selected = (currentWespState == state),
                                                    onClick = { model.wespOperationalState.value = state }
                                                )
                                                Text(state.title, fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    Divider(modifier = Modifier.padding(vertical = 4.dp))

                                    // Pump Toggles
                                    val p01Active by model.p01RecirculationPumpActive.subscribe().collectAsState(true)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = p01Active,
                                            onCheckedChange = { model.p01RecirculationPumpActive.value = it }
                                        )
                                        Text("P-01 Stage 1 Recirculation Pump", fontSize = 13.sp)
                                    }

                                    val p03Active by model.p03RecirculationPumpActive.subscribe().collectAsState(true)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = p03Active,
                                            onCheckedChange = { model.p03RecirculationPumpActive.value = it }
                                        )
                                        Text("P-03 Stage 2 Recirculation Pump", fontSize = 13.sp)
                                    }
                                }
                            }

                            // Section 4: Real-Time Plant KPI Summary Cards
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = 3.dp,
                                shape = RoundedCornerShape(8.dp),
                                backgroundColor = Color(0xFFEFF6FF)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Key Performance Indicators (KPI)", fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))

                                    val so2Delivered by model.deliveredSo2Percent.subscribe().collectAsState(12.0)
                                    val mistOut by model.outletMistConcentration.subscribe().collectAsState(1.48)
                                    val acidBleed by model.weakAcidBleedRate.subscribe().collectAsState(7.68)
                                    val gasOut by model.deliveredGasFlowRate.subscribe().collectAsState(188.0)

                                    val mistCompliant = mistOut <= 5.0
                                    val so2Compliant = so2Delivered in 11.0..13.0

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Delivered SO2 (S-07):", fontSize = 13.sp)
                                        Text(
                                            "${"%.2f".format(so2Delivered)} % (target 11–13%)",
                                            fontWeight = FontWeight.Bold,
                                            color = if (so2Compliant) Color(0xFF047857) else Color(0xFFB91C1C),
                                            fontSize = 13.sp
                                        )
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Outlet H2SO4 Mist:", fontSize = 13.sp)
                                        Text(
                                            "${"%.2f".format(mistOut)} mg/Nm³ (norm ≤ 5.0)",
                                            fontWeight = FontWeight.Bold,
                                            color = if (mistCompliant) Color(0xFF047857) else Color(0xFFB91C1C),
                                            fontSize = 13.sp
                                        )
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Weak Acid Bleed (L-10):", fontSize = 13.sp)
                                        Text("${"%.2f".format(acidBleed)} t/h", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Gas Throughput (S-07):", fontSize = 13.sp)
                                        Text("${"%.1f".format(gasOut)} t/h", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    // --- Right Panel: Real-Time Multi-Chart Visualizations (65% Width) ---
                    second(700.dp) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Chart 1: Gas Tract Flow Rates (t/h)
                            Card(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                elevation = 2.dp,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text(
                                        "Gas Tract Mass Balances [t/h]",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF1E3A8A)
                                    )
                                    TimeSeriesPlot(
                                        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                                        xAxisTitle = "Time",
                                        yAxisTitle = "Rate (t/h)"
                                    ) {
                                        PlotNumericState(
                                            context = context,
                                            state = model.gasInlet.production,
                                            name = "S-01 Gas Inlet Feed (kg/s)",
                                            maxAge = maxAge,
                                            sampling = 500.milliseconds
                                        )
                                        PlotNumericState(
                                            context = context,
                                            state = model.dryingTowerSink.consumation,
                                            name = "S-07 Delivered Gas (kg/s)",
                                            maxAge = maxAge,
                                            sampling = 500.milliseconds
                                        )
                                        PlotNumericState(
                                            context = context,
                                            state = model.dilutionAir.production,
                                            name = "S-06 Dilution Air (kg/s)",
                                            maxAge = maxAge,
                                            sampling = 500.milliseconds
                                        )
                                    }
                                }
                            }

                            // Chart 2: Liquid Buffer Levels (kg / m3)
                            Card(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                elevation = 2.dp,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text(
                                        "Buffer Storage Dynamics [kg]",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF1E3A8A)
                                    )
                                    TimeSeriesPlot(
                                        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                                        xAxisTitle = "Time",
                                        yAxisTitle = "Accumulation (kg)"
                                    ) {
                                        PlotNumericState(
                                            context = context,
                                            state = model.stage1AcidTank.content,
                                            name = "T-01 Stage 1 Acid Tank Level (kg)",
                                            maxAge = maxAge,
                                            sampling = 500.milliseconds
                                        )
                                        PlotNumericState(
                                            context = context,
                                            state = model.stage2AcidTank.content,
                                            name = "T-03 Stage 2 Condensate Tank Level (kg)",
                                            maxAge = maxAge,
                                            sampling = 500.milliseconds
                                        )
                                        PlotNumericState(
                                            context = context,
                                            state = model.weakAcidTank.content,
                                            name = "T-02 Weak Acid Buffer Level (kg)",
                                            maxAge = maxAge,
                                            sampling = 500.milliseconds
                                        )
                                    }
                                }
                            }

                            // Chart 3: Quality KPIs & Environmental Emissions
                            Card(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                elevation = 2.dp,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text(
                                        "Quality & Emission KPIs (Mist mg/Nm³, SO2 %, Bleed t/h)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF1E3A8A)
                                    )
                                    TimeSeriesPlot(
                                        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                                        xAxisTitle = "Time",
                                        yAxisTitle = "KPI Value"
                                    ) {
                                        PlotNumberState(
                                            context = context,
                                            valueState = model.outletMistConcentration,
                                            seriesName = "Outlet H2SO4 Mist (mg/Nm³, norm ≤ 5.0)",
                                            maxAge = maxAge,
                                            sampling = 500.milliseconds
                                        )
                                        PlotNumberState(
                                            context = context,
                                            valueState = model.deliveredSo2Percent,
                                            seriesName = "Delivered SO2 Conc. (%)",
                                            maxAge = maxAge,
                                            sampling = 500.milliseconds
                                        )
                                        PlotNumberState(
                                            context = context,
                                            valueState = model.weakAcidBleedRate,
                                            seriesName = "Weak Acid Bleed Rate (t/h)",
                                            maxAge = maxAge,
                                            sampling = 500.milliseconds
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
