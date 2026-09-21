/// One-shot Value Passed to a handler clause for resuming suspended work.
pub let Continuation = <Input: type, Output: type>: type builtin()

/// Owned, erased action that may perform a handled algebraic effect.
pub let EffectCallable = <Input: type, Output: type, Answer: type>: type builtin()

/// Protocol anchor for compiler-derived effect handlers.
/// Every source `effect` declaration automatically satisfies this trait; the
/// labeled argument schema and `handle` function are synthesized from its operations.
pub let Handle = trait<self: effect> {
  /// Complete argument schema synthesized from the operations of `Self`.
  Arguments: <Value: type, Answer: type>: parameters;
  /// Handles `Self` around `action`, leaving `Rest` as the residual effect row.
  handle: <Value: type, Answer: type, rest: effects>with<rest>
  ...Arguments<Value, Answer>: Answer
}
