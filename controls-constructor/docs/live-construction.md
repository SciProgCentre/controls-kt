# Editing a running constructor

Register properties, actions and child trees on the existing `DeviceConstructor`.
Serialize changes and reads of its mutable collections in the calling application.
These operations do not form a transaction over the whole tree.

`unregisterProperty(name)` removes the property and waits for its publication job
to stop. The source `ValueState` remains owned by its creator. The same name can
then be registered again. Existing references to the old state do not change.

`unregisterAction(name)` removes the name from subsequent lookups. A call already
in progress, or a function returned by `registerAction`, remains valid.

`removeTree(name)` detaches and returns the child. It does not stop the child:
the caller decides whether to reuse it or stop its devices. Stopping a device
does not make that instance restartable.

A constructor sends `DescriptionMessage` at startup and after changes to its
properties or actions while running. Child removal uses the existing tree-change
message. These messages are observations, not acknowledgements from every client.

## Changing a state source

`LateBindValueState.bind` still accepts only the first binding. Use `rebind` to
set or replace it explicitly. Existing subscriptions follow the new state; source
values and timestamps are preserved.

Given two `ValueState<Double>` sources and a constructor:

```kotlin
val input = LateBindValueState(0.0)
input.bind(firstSource)
device.registerProperty("value", MetaConverter.double, input)
device.start()
input.rebind(secondSource)
device.unregisterProperty("value")
```

Switching is observed through the flow, not a synchronous drain of messages
already delivered to consumers. The subscription remains open for later rebinds,
even if the current source flow finishes.

`BoundStateHolder.rebind` exposes the same operation for `ComputationDevice`,
`Alarm` and `Accumulator`, using their existing input names. The default method
rejects rebinding for other implementations. Rebinding an accumulator changes
its input; it does not reset accumulated history.

`BoundStateHolder.bind` remains the initial binding operation. `resolvePropertyState`
is unchanged: replacing a tree node does not automatically rebind states that
were previously resolved from it.

The acquisition configuration, protocol connections and saved configuration
revision are outside these operations. This API does not hot-reload a
`PlcTagTable` or activate a complete installation.
