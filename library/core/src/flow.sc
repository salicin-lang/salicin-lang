/// Trait used by `?.` to transform successful container payloads.
pub let Chain = trait {
  /// Payload type read from the successful case.
  let Item: type
  /// Type constructor used to rebuild the container with a new payload.
  let Rebind = <Value: type>: type

  /// Applies `transform` to the successful payload or propagates the residual case.
  let chain = <e: effects, U: type>with<e>(self)(transform: with<e>(Item) :U): Rebind<U>
  }

/// Trait used by `??` to extract a Value or evaluate a fallback.
pub let Coalesce = trait {
  /// Payload type produced by coalescing.
  let Item: type

  /// Returns the successful payload or evaluates `fallback`.
  let coalesce = <e: effects>with<e>(self)(fallback: with<e>() :Item): Item
}

/// Trait used by postfix `!!` to assert success and extract a payload.
pub let Unwrap = trait {
  /// Payload type produced by unwrapping.
  let Output: type

  /// Returns the successful payload or terminates when no payload is present.
  let unwrap = (move self): Output
}

/// Trait used by postfix `!` to turn a stored failure into `Throws`.
pub let Raise = trait {
  /// Successful payload type.
  let Output: type
  /// Error type introduced into the effect row.
  let Error: type

  /// Returns the successful payload or raises the stored Error.
  let raise = with<core.error.throwing<Error>>(move self): Output
}
