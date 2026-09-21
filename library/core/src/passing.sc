// Compile-time transformations over runtime parameter schemas and passing modes.
/// Changes a runtime parameter schema to copy its argument.
pub let copy: <p: parameters>: parameters = builtin()

/// Changes a runtime parameter schema to move its argument.
pub let move: <p: parameters>: parameters = builtin()
