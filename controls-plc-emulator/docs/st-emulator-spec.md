# IEC 61131-3 Structured Text (ST) Compiler Specification

## 1. Introduction
This document specifies the design and technical requirements for a compiler of the IEC 61131-3 Structured Text (ST) language within the `controls-kt` framework. The compiler translates ST source code into the Instruction List (IL) language, which is subsequently executed by the IL emulator.

## 2. Goals
- Provide a robust and efficient ST-to-IL compilation engine.
- Support standard ST language constructs as defined in IEC 61131-3.
- Ensure seamless integration with the `controls-kt` IL emulator.
- Facilitate high-level logic development for emulated PLC systems.

## 3. Requirements

### 3.1 Language Support
The compiler MUST support core ST constructs, including:
- **Program Organization Units (POUs)**:
    - `PROGRAM` ... `END_PROGRAM`: The main execution units.
    - `FUNCTION` ... `END_FUNCTION`: Reusable code blocks with a return value and no internal state.
    - `FUNCTION_BLOCK` ... `END_FUNCTION_BLOCK`: Reusable code blocks with internal state (instance-based).
- **Variable Declarations**: 
    - `VAR` ... `END_VAR` blocks for local variables.
    - `VAR_INPUT`, `VAR_OUTPUT`, `VAR_IN_OUT` for POU parameters.
    - Support for standard types (`BOOL`, `INT`, `REAL`, `TIME`, etc.) and initial values.
- **Assignments**: The `:=` assignment operator.
- **Expressions**: 
    - Arithmetic: `+`, `-`, `*`, `/`, `MOD`, `**`.
    - Logical: `AND`, `OR`, `XOR`, `NOT`.
    - Comparison: `=`, `<>`, `<`, `>`, `<=`, `>=`.
- **Control Structures**:
    - `IF` ... `THEN` ... `ELSIF` ... `ELSE` ... `END_IF`
    - `CASE` ... `OF` ... `ELSE` ... `END_CASE`
    - `FOR` ... `TO` ... `BY` ... `DO` ... `END_FOR`
    - `WHILE` ... `DO` ... `END_WHILE`
    - `REPEAT` ... `UNTIL` ... `END_REPEAT`
- **Function and Function Block Calls**: Ability to call standard functions and FB instances.

### 3.2 Compilation to IL
The compiler MUST generate IL code that preserves the semantic meaning of the ST source:
- **POU Mapping**: Each `PROGRAM`, `FUNCTION`, and `FUNCTION_BLOCK` MUST be compiled into an `IlProgramBlock` or a similar structure that can be identified and invoked.
- **FB Instantiation**: Calling a `FUNCTION_BLOCK` MUST involve a `CAL` instruction targeting the specific instance's data.
- **Expression Decomposition**: Expressions MUST be decomposed into a sequence of IL instructions using the accumulator model (`LD`, `ST`, arithmetic/logical operators).
- **Control Flow**: Control structures MUST be implemented using appropriate jump instructions (`JMP`, `JMPC`, `JMPCN`) and labels.
- **Scoping**: ST variable scopes (local, input, output) MUST be correctly mapped to IL variable declarations within the respective blocks.

### 3.3 Parsing
- The compiler MAY use the [parsus](https://github.com/alllex/parsus) library for parsing ST source code.
- The parser MUST handle standard ST syntax, including comments (`(* ... *)` and `//`).
- Syntax errors MUST be reported with precise location information (line and column).

### 3.4 Integration
- The compiler output MUST be compatible with the `IlProgramBlock` structure used by the IL emulator.
- The compiler SHOULD provide an API to compile ST strings or files directly into executable `IlProgramBlock` instances.

### 3.5 Extensibility
- The compiler SHOULD support custom functions and function blocks that can be mapped to IL `CAL` instructions or inline IL sequences.
- A mechanism for adding vendor-specific ST extensions SHOULD be considered.

## 4. Architecture

### 4.1 Compilation Pipeline
1. **Frontend**: Lexical and syntactic analysis to produce an Abstract Syntax Tree (AST).
2. **Middle-end**: Semantic analysis, including type checking and symbol table management.
3. **Backend**: Code generation that transforms the AST into a list of IL instructions.

### 4.2 Mapping Examples
| ST Construct | Generated IL (Conceptual) |
| :--- | :--- |
| `A := B + C;` | `LD B`, `ADD C`, `ST A` |
| `IF A THEN B := 1; END_IF;` | `LD A`, `JMPCN label_end`, `LD 1`, `ST B`, `label_end:` |
| `FOR i := 1 TO 10 DO ... END_FOR;` | `LD 1`, `ST i`, `label_loop:`, `LD i`, `LE 10`, `JMPCN label_end`, `...`, `LD i`, `ADD 1`, `ST i`, `JMP label_loop`, `label_end:` |
| `MyFBInstance();` | `CAL MyFBInstance` |
| `Res := MyFunc(In1, In2);` | `LD In1`, `MyFunc In2`, `ST Res` (depends on IL implementation of functions) |

## 5. Implementation Considerations

### 5.1 Optimization
- **Constant folding**: Evaluate constant expressions at compile time.
- **Accumulator optimization**: Minimize redundant `LD` and `ST` instructions by tracking the current accumulator state.

### 5.2 Error Handling
- **Compilation Errors**: Clear distinction between syntax errors and semantic errors (e.g., type mismatch).
- **Warnings**: The compiler MAY issue warnings for potentially problematic code (e.g., unreachable code, unused variables).

### 5.3 Local Variables
The compiler MUST manage temporary variables required for complex expression evaluation in ST that cannot be directly mapped to the IL accumulator.
