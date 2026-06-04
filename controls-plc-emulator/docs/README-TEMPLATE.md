# IEC 61131-3 IL Emulator

This module provides an emulator for the IEC 61131-3 Instruction List (IL) language.

## Features
- Full support for standard IL operators (`LD`, `ST`, `ADD`, `SUB`, `MUL`, `DIV`, `AND`, `OR`, `XOR`, `JMP`, `CAL`, etc.).
- Integration with `controls-kt` via `PlcEmulatorScope`.
- Support for custom vendor-specific instructions.
- Concurrent execution of multiple IL programs using Kotlin coroutines.
- Parsing using the `parsus` library.

## Usage
To run an IL program, you need an instance of `PlcEmulatorScope` and the IL source code.

```kotlin
val scope: PlcEmulatorScope = ...
val source = """
    LD 10
    ADD 20
    ST result
"""
scope.launchIl(source).join()
```

For more details, see the [specification](il-emulator-spec.md).
