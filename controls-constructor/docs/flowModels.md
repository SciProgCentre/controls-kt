# Flow Models

The `flowModels` feature provides specialized components for modeling the flow of material, energy, or information. It includes support for both continuous and discrete flows.

## Continuous Flow Models

Continuous models are used for systems where matter or energy flows continuously, such as liquids in pipes or electric current.

### Core Concepts

- **ContinuousProducer**: A source of flow. It provides its `productionCapacity`.
- **ContinuousConsumer**: A sink of flow. It provides its `consumationCapacity`.
- **ContinuousFlowModel.connect()**: A helper to wire a producer and a consumer together.

### Pre-built Components

- **ContinuousBuffer**: Stores a certain amount of material and allows it to flow in and out.
- **ContinuousProducer**: Generates flow based on its internal state.
- **ContinuousConsumer**: Consumes flow from a source.
- **ContinuousMix**: Combines multiple input flows into a single output flow.
- **ContinuousSeparate**: Splits a single input flow into multiple output flows.
- **ContinuousReaction**: Models a process where input materials are converted into output materials (e.g., chemical reaction).
- **ContinuousTransformer**: Transforms the properties of the flow without changing the amount.

## Discrete Flow Models

Discrete models are used for systems where items or events move in discrete steps, such as a conveyor belt with parts.

(Details on discrete models can be added here if needed, based on `space.kscience.controls.constructor.models.discrete`).

## Demos and Tests

- **Tests**: 
    - `../src/commonTest/kotlin/space/kscience/controls/constructor/ContinuousFlowTest.kt`
    - `../src/commonTest/kotlin/space/kscience/controls/constructor/DiscreteFlowTest.kt`
- **Demos**: `../../demo/constructor`

<!-- LLM generated code: Documentation for FlowModels feature -->
