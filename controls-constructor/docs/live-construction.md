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
