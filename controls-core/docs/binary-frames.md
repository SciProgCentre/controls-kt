# Binary Frame Processing in controls-core

The `space.kscience.controls.binary` package provides a set of interfaces and classes for processing binary data frames in a reactive way using Kotlin Flows. These frames are represented as `Envelope` objects from the DataForge IO library.

## Core Concepts

The frame processing framework is built around three primary functional interfaces:

- **`FrameProducer`**: An entity that produces a stream of binary frames.
    - `subscribe()`: Returns a `Flow<Envelope>` of produced frames.
    - `telemetry`: A `Flow<FrameTelemetry>` providing real-time information about frame production and processing (start/finish times, success status, and metadata).
- **`FrameConsumer`**: A functional interface for entities that can receive frames. It defines a single `suspend fun send(frame: Envelope)`.
- **`FrameTransformer`**: A functional interface for transforming one frame into another. It defines `suspend fun transform(frame: Envelope): Envelope`.

## FrameProcessor

`FrameProcessor` is the central implementation that bridges producers and consumers. It implements both `FrameProducer` and `FrameConsumer`, allowing it to be used as a middleware in a processing pipeline.

Key features of `FrameProcessor`:
- **Buffering**: It uses an internal Coroutine `Channel` to buffer incoming frames.
- **Sequential Processing**: Frames are processed one-by-one using a provided `FrameTransformer`.
- **Telemetry**: It automatically records and emits telemetry for every processed frame, including processing duration and any errors encountered.
- **Monitoring**: It exposes `queueLength` as a `StateFlow<Int>`, allowing for monitoring of backpressure.

## Device Integration

Binary frame processing is often used to handle data coming directly from devices (e.g., camera frames, oscilloscope traces).

- **`PeerConnection`**: A thin abstraction for point-to-point synchronous binary data exchange. It allows sending and receiving `Envelope`s to specific addresses.
- **`DeviceFrameProducer`**: A specialized producer that integrates with the standard `DeviceMessage` model. It:
    1. Subscribes to `BinaryNotificationMessage`s from a `DeviceMessageSource`.
    2. Uses a `PeerConnection` to fetch the actual binary data associated with the notification.
    3. Emits the fetched data as a frame for further processing.

## FrameProcessingGraph

For complex scenarios involving multiple processing steps, `FrameProcessingGraph` provides a DSL to construct and monitor a pipeline of nodes.

```kotlin
// LLM generated code: Example of using FrameProcessingGraph to build a pipeline
val graph = context.FrameProcessingGraph {

    // Register a source node
    val cameraNode = producer("camera", cameraFrameProducer)

    // Define a transformation node
    val grayscaleNode = cameraNode.transform("grayscale") { frame ->
        // Transformation logic here
        frame
    }

    // Subscribing a consumer to a node
    grayscaleNode.subscribe(scope) { frame ->
        println("Received processed frame: ${frame.dataID}")
    }
}
```

The graph serves as a single point for managing and monitoring the entire pipeline, where each `Node` keeps track of its producer and its source dependencies.
