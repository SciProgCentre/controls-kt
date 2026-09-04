# Module controls-utilities

Utility devices

- [Alarm Device](./docs/alarm.md): Multi-stage virtual alarm device with dynamic threshold settings and reactive status evaluation.
- [Accumulator Device](./docs/accumulator.md): Virtual device that sums numeric samples over a sliding time window.

Create utility components without a source, then connect their default or `value` input with
`bind(ValueState<Meta>)` or a `ConstructorBinding`.

## Usage

## Artifact:

The Maven coordinates of this project are `space.kscience:controls-utilities:0.4.1-dev`.

**Gradle Kotlin DSL:**
```kotlin
repositories {
    maven("https://repo.kotlin.link")
    mavenCentral()
}

dependencies {
    implementation("space.kscience:controls-utilities:0.4.1-dev")
}
```
