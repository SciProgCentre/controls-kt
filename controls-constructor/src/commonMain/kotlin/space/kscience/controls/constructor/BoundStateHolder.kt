package space.kscience.controls.constructor

import space.kscience.dataforge.meta.Meta

/**
 * A container for input properties that could be bound to a state
 */
public interface BoundStateHolder {
    /**
     * Bind input with name [inputName] of this holder to external state [state].
     */
    public fun bind(state: ValueState<Meta>, inputName: String = DEFAULT_INPUT_NAME)

    /** Set or replace an input source when this holder supports rebinding. */
    public fun rebind(state: ValueState<Meta>, inputName: String = DEFAULT_INPUT_NAME) {
        throw UnsupportedOperationException("Rebinding is not supported")
    }

    public companion object{
        public const val DEFAULT_INPUT_NAME: String = ""
    }
}