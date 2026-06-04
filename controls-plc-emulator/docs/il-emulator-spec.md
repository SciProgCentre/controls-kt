# IEC 61131-3 Instruction List (IL) Emulator Specification

## 1. Introduction
This document specifies the design and technical requirements for an emulator of the IEC 61131-3 Instruction List (IL) language within the `controls-kt` framework.

## 2. Goals
- Provide a robust and extensible IL execution engine.
- Integrate seamlessly with the `controls-kt` device model via `PlcEmulatorScope`.
- Support high-concurrency execution using Kotlin coroutines.
- Allow vendor-specific instruction sets.

## 3. Requirements

### 3.1 Language Support
- The emulator MUST support standard IEC 61131-3 IL operators, including but not limited to:
    - `LD`, `LDN` (Load / Load Negated)
    - `ST`, `STN` (Store / Store Negated)
    - `S`, `R` (Set / Reset)
    - `AND`, `ANDN`, `OR`, `ORN`, `XOR`, `XORN` (Bitwise operations)
    - `ADD`, `SUB`, `MUL`, `DIV` (Arithmetic operations)
    - `GT`, `GE`, `EQ`, `NE`, `LE`, `LT` (Comparison)
    - `JMP`, `JMPC`, `JMPCN` (Jump / Conditional Jump)
    - `CAL`, `CALC`, `CALCN` (Call / Conditional Call)
    - `RET`, `RETC`, `RETCN` (Return)

### 3.2 Parsing
- The emulator MAY use the [parsus](https://github.com/alllex/parsus) library for parsing IL source code.
- The parser SHOULD handle labels, operators, modifiers, and operands.

### 3.3 Concurrency
- Each IL program execution MUST be encapsulated in a separate Kotlin `Job`.
- The emulator MUST support multiple IL programs running concurrently within the same `PlcEmulatorScope`.
- Implementations MUST take into account concurrent modification of registry values and external variables.

### 3.4 Integration
- The emulator MUST use `PlcEmulatorScope` for interacting with registers, variables, and external functions.
- The changes in external data is done via `PlcEmulatorScope` subscription.

### 3.5 Extensibility
- The engine MUST allow adding additional vendor-specific instructions.
- A mechanism for registering custom operator handlers SHOULD be provided.

## 4. Architecture

### 4.1 Execution Context
The execution state of a single IL program includes:
- **Accumulator (CR)**: The primary register for operations.
- **Program Counter (PC)**: Index of the current instruction.
- **Local Memory**: Variables specific to the program instance.
- **PlcEmulatorScope**: Interface to the global PLC state.

### 4.2 Instruction Execution Loop
The engine executes instructions in a loop:
1. Fetch the instruction at `PC`.
2. Evaluate modifiers (e.g., check condition for `JMPC`).
3. Execute the operator logic using the `Accumulator` and `Operand`.
4. Update the `Accumulator` with the result.
5. Update `PC` (increment or jump).

### 4.3 PlcEmulatorScope
The `PlcEmulatorScope` interface provides the following contract:
```kotlin
public interface PlcEmulatorScope : ContextAware {
    public val clockManager: ClockManager
    public suspend fun read(identifier: String): Meta
    public suspend fun write(identifier: String, value: Meta)
    public suspend fun call(identifier: String, arguments: Meta): Meta
    public fun subscribe(identifier: String): Flow<Meta>
}
```
*Note: `PlcEmulatorScope` may be extended or modified to support IL-specific needs.*

## 5. Implementation Considerations

### 5.1 Atomicity
Since multiple IL programs can run concurrently, access to shared resources in `PlcEmulatorScope` must be handled carefully. The `PlcEmulatorScope` implementation is responsible for ensuring the consistency of the underlying data platform.

### 5.2 Error Handling
- Parsing errors MUST be reported with line and column information.
- Runtime errors (e.g., division by zero, missing variable) SHOULD be handled via Kotlin exceptions and can be monitored through the `Job` status.

### 5.3 Timing
The `clockManager` in `PlcEmulatorScope` SHOULD be used for any time-related operations or delays within the IL programs.
