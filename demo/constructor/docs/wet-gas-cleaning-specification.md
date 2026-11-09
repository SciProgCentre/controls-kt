# Wet Gas Cleaning Plant Demonstrator Specification

## 1. Executive Summary & Objectives

This document specifies the technical architecture, continuous flow topology, and interactive user interface for implementing a digital twin demonstrator of the **Wet Gas Cleaning Plant**.

The demonstrator is designed as an executable simulation model within the `demo/constructor` module, built on the reactive continuous flow modeling primitives of `controls-models-flow` and visualized using JetBrains Compose Desktop with `TimeSeriesPlot`.

### Key Objectives
- **Dynamic Mass Balance Simulation**: Model continuous gas and liquid streams, moisture condensation, SO3 absorption into weak sulfuric acid, and mist separation in real time.
- **Buffer & Cascade Dynamics**: Simulate acid storage buffers, cascade overflows, and weak acid bleed regulation.
- **Interactive Operational Controls**: Provide operator controls to modulate gas throughput, inlet SO2/dust variations, and equipment availability (pump trips, WESP maintenance).
- **KPI Monitoring**: Display real-time key performance indicators such as outlet H2SO4 mist concentration, SO2 percentage, and weak acid discharge.

---

## 2. Scope & Demarcation

### 2.1 In Scope (Core Technological Nodes)
- **Gas Tract**:
  - Hot sulfurous gas inlet (`SRC-01`).
  - Stage 1 quench / cooling tower (`B-01`): adiabatic saturation, dust washout, and SO3-to-H2SO4 conversion.
  - Stage 2 condensation tower (`B-02`): moisture condensation and fine dust capture.
  - Two-stage Wet Electrostatic Precipitators (`B-03` and `B-04`): mist and aerosol separation.
  - Gas mixing tower (`B-05`): dilution with air/O2 to target SO2 concentration.
  - Gas delivery sink to drying section (`SNK-01`).
- **Liquid Recirculation & Bleed Circuits**:
  - Stage 1 acid collection tank (`T-01`) and recirculation pumps (`P-01`/`P-02`).
  - Stage 2 acid buffer tank (`T-03`) with cooling recirculation (`P-03`/`E-01`).
  - Liquid cascade overflow from Stage 2 to Stage 1 (`L-13`).
  - WESP drainage loop (`L-14`).
  - Weak acid stripping column (`B-08`), buffer tank (`T-02`), and neutralization discharge sink (`SNK-02`).
  - Fresh make-up water feed (`W-01`).

### 2.2 Out of Scope (Skipped Non-Core Nodes & Rationale)

| Technological Component | Physical Phenomenon | Rationale for Skipping in Core | Demonstrator Representation |
|---|---|---|---|
| **E-01 Plate Heat Exchanger Internals** | Multi-phase convective ODE thermal solvers ($Q = U \cdot F \cdot \Delta T_{\text{log}}$) | `controls-models-flow` is a mass/flow-balance framework without a built-in multi-phase thermal differential solver. | Heat removal (13.9 MW nominal) is abstracted macroscopically into the moisture condensation knockout fraction of `B-02`. |
| **F-01 Filter Press & B-07 Sludge Thickener** | Discontinuous discrete batch cycles (fill-press-discharge) | Discrete batch cycles complicate the continuous flow topology and balance equations. | Sludge removal is represented as a small continuous side-stream or external boundary sink. |
| **WESP Electrical Breakdown & Corona Dynamics** | Microsecond sparkover transients and electrostatic field microphysics | High-frequency electrical transients are outside continuous mass-balance scopes. | Macroscopic separation efficiency modeled via steady-state Deutsch equation: $\eta = 1 - \exp\left(-\frac{w A}{Q}\right)$. |
| **Industrial AI Optimizers (Sec. 10)** | Machine learning inference and supervisory model predictive control | AI models are external supervisory layers. | Exposed data streams and observable states (`debugState`, `displayState`) allow future external AI integration. |

---

## 3. Technological Nodes & Core Flow Primitives Mapping

The demonstrator maps plant apparatuses into `controls-models-flow` components:

```
[SRC-01: Gas Source] --> [B-01: Quench Separator] --> [B-02: Condenser Separator] --> [B-03/B-04: WESP Mist Separator] --> [B-05: Gas Mixer] --> [SNK-01: Drying Section]
                                 ^                             |                                   |
                                 |                             v                                   v
[W-01: Make-up Water] ----> [T-01 Acid Buffer] <------- [T-03 Condensate Buffer] <-----------------+
                                 |
                                 v
                        [B-08: SO2 Stripper] --> [T-02: Weak Acid Buffer] --> [SNK-02: Neutralization]
```

### Detailed Node Specifications

| Apparatus ID | Physical Function | Core Flow Primitive | Unit / Algebra | Parameters & Equations |
|---|---|---|---|---|
| **SRC-01** | Hot sulfurous gas source | `ContinuousProducer` | `MixtureAlgebra` / `Kilograms` | Nom. 188 t/h total gas (60 t/h SO2, 18% vol dry, 6% H2O, 0.2 g/Nm³ dust). Rate adjustable via slider. |
| **B-01** | Quench cooling & saturation tower | `ContinuousSeparate` + `ContinuousReaction` | `Kilograms` / `AmountPerSecond` | Adiabatic saturation: captures 70% dust, converts SO3 to H2SO4 mist (10% to gas stream, 90% to liquid). |
| **T-01** | Stage 1 acid collection tank | `ContinuousBuffer` | `Kilograms` | Working volume: 80 m³. Accumulates circulating 10% H2SO4; receives L-13 cascade and W-01 make-up water. |
| **P-01 / P-02** | Stage 1 acid recirculation pumps | Rate-limited flow connection | `AmountPerSecond` | 2 × 400 m³/h capacity; toggleable states for failure simulation. |
| **B-02** | Condensation & cooling tower | `ContinuousSeparate` | `Kilograms` | Moisture condensation: splits excess water vapor (~19.3 t/h at nominal) into liquid condensate drain `L-12`. |
| **T-03** | Stage 2 acid buffer tank | `ContinuousBuffer` | `Kilograms` | Working volume: 60 m³. Collects condensate and WESP drainage; overflows via `L-13` to `T-01`. |
| **P-03 / E-01** | Stage 2 recirculation circuit | Rate-limited flow connection | `AmountPerSecond` | 530 m³/h circulation; cools gas to 40 °C. |
| **B-03 / B-04** | Two-stage Wet Electrostatic Precipitators | `ContinuousSeparate` | `Kilograms` | Deutsch rule: $\eta = 1 - \exp(-wA/Q)$ (nom. 95% per stage). Drains captured mist to `T-03`. |
| **B-05** | Gas mixing tower | `ContinuousMix` | `MixtureAlgebra` | Combines cleaned gas (`S-05`) with dilution air/O2 (`S-06`, nom. 58,500 Nm³/h) to maintain 12% SO2. |
| **B-08** | SO2 stripping column | `ContinuousSeparate` | `Kilograms` | Strips 95% of dissolved SO2 from weak acid bleed; gas returns to duct. |
| **T-02 / P-05** | Weak acid buffer & discharge pump | `ContinuousBuffer` | `Kilograms` | Buffers 10% weak acid before transfer to neutralization (`SNK-02`, nom. 7.7 t/h). |
| **SNK-01** | Drying tower delivery sink | `ContinuousConsumer` | `Kilograms` | Validates gas delivery constraints (SO2: 11–13%, mist $\le$ 5 mg/Nm³). |
| **SNK-02** | Neutralization boundary sink | `ContinuousConsumer` | `Kilograms` | Accepts neutralized weak acid stream (nom. 7.6–7.7 t/h). |

---

## 4. Flow Network Topology & Mathematical Balances

### 4.1 Gas Path Streams

1. **`S-01` (Inlet Feed)**: $124,100\ \text{Nm}^3/\text{h}$ wet ($116,600\ \text{Nm}^3/\text{h}$ dry, $188\ \text{t/h}$), $330\ ^\circ\text{C}$, $\text{SO}_2 = 18.0\%$, $\text{SO}_3 = 0.15\%$, $\text{H}_2\text{O} = 6.0\%$, dust $= 0.2\ \text{g/Nm}^3$.
2. **`S-02` (Post Quench B-01)**: $116,400\ \text{Nm}^3/\text{h}$ dry $+ 27.1\ \text{t/h}$ vapor, saturated at $62.9\ ^\circ\text{C}$, $\text{H}_2\text{O} = 22.5\%$, acid mist $= 660\ \text{mg/Nm}^3$.
3. **`S-03` (Post Condenser B-02)**: $116,400\ \text{Nm}^3/\text{h}$ dry $+ 7.8\ \text{t/h}$ vapor, cooled to $40\ ^\circ\text{C}$, $\text{H}_2\text{O} = 7.7\%$, mist $= 590\ \text{mg/Nm}^3$.
4. **`S-04` (Post WESP-1 B-03)**: Mist reduced by 95% down to $30\ \text{mg/Nm}^3$.
5. **`S-05` (Post WESP-2 B-04)**: Mist reduced by 95% down to $1.5\ \text{mg/Nm}^3$ (target $\le 5\ \text{mg/Nm}^3$), dust $\le 0.2\ \text{mg/Nm}^3$.
6. **`S-06` (Dilution Air / O2)**: $58,500\ \text{Nm}^3/\text{h}$ dry air.
7. **`S-07` (Delivery to Drying Tower SNK-01)**: $175,000\ \text{Nm}^3/\text{h}$ dry, $\text{SO}_2 = 12.0\%$, $\text{O}_2 = 10.3\%$, $T = 35\text{--}40\ ^\circ\text{C}$.

### 4.2 Liquid & Water Balances

```
Incoming Water (t/h)                   Outgoing Water (t/h)
---------------------                   --------------------
Vapor with inlet gas (S-01):  5.98      Vapor in outlet gas (S-07):  7.83
Make-up water (W-01):         5.89      Water in weak acid (L-10):   6.89
WESP wash water (W-02):       3.00      H2O consumed in SO3->H2SO4:  0.14
                                        Moisture in sludge cake:     0.01
----------------------------------      ---------------------------------
Total Inflow:                14.87      Total Outflow:              14.87
```

- **Internal Loop Turnover**: $21.2\ \text{t/h}$ evaporated in `B-01`; $19.3\ \text{t/h}$ condensed in `B-02`.
- **Cascade Overflow (`L-13`)**: $22.4\ \text{t/h}$ transfers from `T-03` to `T-01`.
- **Weak Acid Bleed (`L-10`)**: $7.7\ \text{t/h}$ ($10\%\ \text{H}_2\text{SO}_4$, $765\ \text{kg/h}\ \text{H}_2\text{SO}_4$).

---

## 5. Key Adjustable Factory Parameters for Live Demonstration

To maximize demonstration utility, operator interactivity, and dynamic feedback during simulations, eight primary parameters are exposed for runtime manipulation via UI controls:

| # | Parameter Name | Target Node / Stream | Nominal Value | Demonstration Range | Step / Type | Physical Impact & Demonstration Utility |
|---|---|---|---|---|---|---|
| **1** | **Inlet Gas Mass Flow Rate ($Q_{\text{gas}}$)** | `SRC-01` (`S-01`) | $188.0\ \text{t/h}$ ($116.6\ \text{kNm}^3/\text{h}$ dry) | $50.0\text{--}250.0\ \text{t/h}$ | $1.0\ \text{t/h}$ (Slider) | Modulates total plant throughput; demonstrates proportional scaling of gas/liquid flows, tower velocities, and load shifts (nominal $60\ \text{t/h}\ \text{SO}_2$ vs $30\ \text{t/h}$ turndown). |
| **2** | **Inlet $\text{SO}_2$ Concentration ($C_{\text{SO2, in}}$)** | `SRC-01` (`S-01`) | $18.0\%\ \text{vol dry}$ | $8.0\%\text{--}28.0\%$ | $0.5\%$ (Slider) | Demonstrates rich vs lean gas dynamics; lean gas expands total volumetric flow, increases WESP velocity, reduces residence time, and pushes mist emissions higher. |
| **3** | **Inlet $\text{SO}_3$ Precursor Fraction ($C_{\text{SO3, in}}$)** | `SRC-01` (`S-01`) | $0.15\%\ \text{vol dry}$ ($\sim 400\ \text{kg/h}$) | $0.05\%\text{--}0.50\%$ | $0.01\%$ (Slider) | Regulates $\text{H}_2\text{SO}_4$ generation in quench tower `B-01`, baseline aerosol mist loading, and required weak acid discharge rate ($L\text{-}10$) to neutralization. |
| **4** | **Inlet Dust Concentration ($C_{\text{dust, in}}$)** | `SRC-01` (`S-01`) | $0.20\ \text{g/Nm}^3$ ($\sim 23.3\ \text{kg/h}$) | $0.05\text{--}1.00\ \text{g/Nm}^3$ | $0.05\ \text{g/Nm}^3$ (Slider) | Showcases particulate scrubber washout efficiency in `B-01`/`B-02` and solids accumulation in `T-01` affecting sludge filtration bleed. |
| **5** | **Dilution Air / $\text{O}_2$ Addition Flow ($Q_{\text{air}}$)** | `S-06` into `B-05` | $58,500\ \text{Nm}^3/\text{h}$ ($75.5\ \text{t/h}$) | $0\text{--}100,000\ \text{Nm}^3/\text{h}$ | $1,000\ \text{Nm}^3/\text{h}$ (Slider) | Governs final $\text{SO}_2$ percentage delivered to drying section (`SNK-01`, target $11.0\text{--}13.0\%$) and adjusts $\text{O}_2/\text{SO}_2$ ratio for catalyst oxidation. |
| **6** | **Make-Up Water Inflow Rate ($Q_{\text{water}}$)** | `W-01` into `T-01` | $5.89\ \text{t/h}$ | $0.0\text{--}15.0\ \text{t/h}$ | $0.1\ \text{t/h}$ (Slider) | Direct control over Stage 1 liquid buffer level (`T-01`), compensating for evaporation in `B-01` and stabilizing circuit acid concentration. |
| **7** | **Stage 2 Condenser Gas Outlet Temp ($T_{\text{gas, B02}}$)** | `B-02` / `E-01` | $40.0\ ^\circ\text{C}$ ($\sim 19.3\ \text{t/h}$ condensate) | $30.0\ ^\circ\text{C}\text{--}65.0\ ^\circ\text{C}$ | $0.5\ ^\circ\text{C}$ (Slider) | Represents `E-01` cooling capacity; directly affects moisture knockout rate into `T-03`, cascade overflow `L-13`, and residual gas moisture into drying section. |
| **8** | **WESP Stage Operational State ($\text{HV}_{\text{WESP}}$)** | `B-03` / `B-04` | Both Stages Active ($99.75\%$ eff.) | 4 Operational States | Discrete Enum / Buttons | Toggles high-voltage field states (Both Active / Stage 1 Only / Stage 2 Only / Washing Mode / Tripped); directly validates outlet mist compliance ($\le 5\ \text{mg/Nm}^3$). |

---

## 6. Demonstrator Architecture in Kotlin DSL

The demonstrator extends `ContinuousFlowModel` following the conventions in `ChemicalFactory.kt` and `EnrichmentFacility.kt`.

### 6.1 Model Declaration Outline

```kotlin
// LLM generated code for Wet Gas Cleaning Plant Demonstrator
package space.kscience.controls.constructor.demo

import space.kscience.controls.constructor.models.flow.continuous.*
import space.kscience.dataforge.context.Context
import space.kscience.dataforge.names.parseAsName

public class WetGasCleaningPlant(context: Context) : ContinuousFlowModel(context) {

    // 1. Gas Ingress & Utilities
    public val gasInlet by producer<Kilograms>(Kilograms)
    public val dilutionAir by producer<Kilograms>(Kilograms)
    public val makeUpWater by producer<Kilograms>(Kilograms)

    // 2. Stage 1 Quench & Recirculation
    public val quenchTower by separate<Kilograms, Kilograms>(Kilograms, Kilograms)
    public val stage1AcidTank by buffer<Kilograms>(Kilograms)

    // 3. Stage 2 Condensation & Recirculation
    public val condenserTower by separate<Kilograms, Kilograms>(Kilograms, Kilograms)
    public val stage2AcidTank by buffer<Kilograms>(Kilograms)

    // 4. WESP Two-Stage Separation
    public val wespStage1 by separate<Kilograms, Kilograms>(Kilograms, Kilograms)
    public val wespStage2 by separate<Kilograms, Kilograms>(Kilograms, Kilograms)

    // 5. Gas Mixing & Delivery
    public val gasMixer by mix<Kilograms>(Kilograms)
    public val dryingTowerSink by consumer<Kilograms>(Kilograms)

    // 6. Weak Acid Stripping & Neutralization
    public val so2Stripper by separate<Kilograms, Kilograms>(Kilograms, Kilograms)
    public val weakAcidTank by buffer<Kilograms>(Kilograms)
    public val neutralizationSink by consumer<Kilograms>(Kilograms)

    init {
        // Topology Wiring & Flow Bindings
        // S-01 -> B-01 -> B-02 -> B-03 -> B-04 -> B-05 -> SNK-01
        // T-03 -> L-13 -> T-01 -> L-08 -> B-08 -> T-02 -> SNK-02
    }
}
```

---

## 7. Interactive User Interface Specification

The UI is built using Compose Multiplatform with a `HorizontalSplitPane` layout matching `ChemicalFactory.kt`.

### 7.1 Layout Wireframe

```
+------------------------------------+---------------------------------------------+
| OPERATOR CONTROLS PANEL (Left 35%) | REAL-TIME MONITORING CHARTS (Right 65%)     |
+------------------------------------+---------------------------------------------+
| [Adjustable Parameters (Sliders)]  | [TimeSeriesPlot 1: Gas Tract Rates]         |
| 1. Gas Feed Rate:   [188 t/h ---o-]|   --- S-01 Inlet Gas Feed (t/h)             |
| 2. Inlet SO2 Conc:  [18.0%  ---o-] |   --- S-07 Cleaned Gas to Drying (t/h)      |
| 3. Inlet SO3 Frac:  [0.15%  ---o-] |                                             |
| 4. Inlet Dust Load: [0.20g  ---o-] +---------------------------------------------+
| 5. Dilution Air:    [58.5kNm3--o-] | [TimeSeriesPlot 2: Buffer Tank Volumes]     |
| 6. Make-Up Water:   [5.89t/h---o-] |   --- T-01 Stage 1 Acid Tank Level (m3)     |
| 7. Condenser T-out: [40.0°C ---o-] |   --- T-03 Stage 2 Condensate Tank (m3)     |
|                                    |   --- T-02 Weak Acid Storage Level (m3)     |
| [Equipment & State Toggles]        +---------------------------------------------+
| 8. WESP State: [Both|S1|S2|Wash|Off| [TimeSeriesPlot 3: Quality & Emission KPIs] |
| [x] P-01 Stage 1 Recirculation Pump|   --- Outlet H2SO4 Mist (mg/Nm3, norm <= 5) |
| [x] P-03 Stage 2 Recirculation Pump|   --- Weak Acid Bleed Rate (t/h)            |
|                                    |   --- Delivered SO2 Concentration (%)       |
| [Numeric KPI Cards]                |                                             |
| - Cleaned Gas SO2:    12.0 %       |                                             |
| - Outlet Mist:        1.48 mg/Nm3  |                                             |
| - Weak Acid Bleed:    7.68 t/h     |                                             |
+------------------------------------+---------------------------------------------+
```

---

## 8. Simulation & Validation Scenarios

The demonstrator model will be verified against the following benchmark scenarios from Section 9 of the technological card:

| Scenario ID | Test Regime | Input Perturbation | Expected Model Response & Verification Criterion |
|---|---|---|---|
| **SC-01** | Nominal Load | $188\ \text{t/h}$ gas ($60\ \text{t/h}\ \text{SO}_2$, $18\%$), $330\ ^\circ\text{C}$ | Mass balances converge within $\pm 2\%$; mist $\le 1.5\ \text{mg/Nm}^3$; acid bleed $7.7\ \text{t/h}$; tank levels stable at $50\text{--}55\%$. |
| **SC-02** | Load Reduction (50%) | $\text{SO}_2$ throughput reduced to $30\ \text{t/h}$ | Water make-up drops to $\sim 1.4\ \text{t/h}$; weak acid bleed drops to $\sim 3.8\ \text{t/h}$; level controllers stabilize buffers. |
| **SC-03** | Lean Gas Ingress | $\text{SO}_2 = 12\%$ with air in-leakage ($60\ \text{t/h}\ \text{SO}_2$) | Gas volumetric flow increases to $64\ \text{m}^3/\text{s}$; WESP residence time decreases; outlet mist increases to $\sim 10.9\ \text{mg/Nm}^3$ (triggers alarm). |
| **SC-04** | P-01 Recirculation Trip | Stage 1 pump flow drops to 0 | Triggers emergency condition `EV-1`; simulates rapid temperature rise warning and emergency water branch activation. |
| **SC-05** | WESP Flushing Event | One WESP field de-energized for wash | Temporary efficiency reduction per stage ($95\% \to 78\%$); outlet mist rises to $\sim 6.6\ \text{mg/Nm}^3$ during wash interval. |

---

## 9. Summary of Deliverables & References

- **Specification Path**: `demo/constructor/docs/wet-gas-cleaning-specification.md`
- **Source Technological Card**: `demo/constructor/docs/chemical-complex.md`
- **Reference Model Implementations**:
  - `demo/constructor/src/jvmMain/kotlin/ChemicalFactory.kt`
  - `demo/constructor/src/jvmMain/kotlin/ChemicalFactory2.kt`
  - `demo/constructor/src/jvmMain/kotlin/EnrichmentFacility.kt`
