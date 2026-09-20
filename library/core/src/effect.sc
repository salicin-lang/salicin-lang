/// One-shot Value Passed to a handler clause for resuming suspended work.
pub let Continuation = <Input: type, Output: type>: type builtin()

/// Owned, erased action that may perform a handled algebraic effect.
pub let EffectCallable = <Input: type, Output: type, Answer: type>: type builtin()

/// Protocol anchor for compiler-derived effect handlers.
/// Every source `effect` declaration automatically satisfies this trait; the
/// operation clauses and `Handle` member are synthesized from that operation set.
pub let Handle = trait<self: effect> {
  /// Clause parameter schema synthesized from the operations of `Self`.
  let Clauses = <Value: type, Answer: type>: parameters
  /// Handles `Self` around `action`, leaving `Rest` as the residual effect row.
  let handle = <Value: type, Answer: type, rest: effects>with<rest>
    ...Clauses<Value, Answer>{move action: with<self, rest>() :Value}: Answer
}
