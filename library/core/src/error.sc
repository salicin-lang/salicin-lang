/// Typed non-local failure effect.
pub let throwing = <Error: type> effect {
  /// Raises `error` and does not return normally.
  raise(move error: Error): never
}

/// Handles `throwing<Error>` from `action` and returns a `Result`.
pub let try = { <f: effects, T: type, Error: type>with<f>{move action: with<core.error.throwing<Error>, f>() :T}: core.Result<Error><T> =>
    core.error.throwing<Error>.handle(action()) {
    raise(error) => core.Result.Err(error),
    Return(value) => core.Result.Ok(value),
  }
}

/// Raises a value through `throwing<Error>`.
pub let throw = { <Error: type>with<core.error.throwing<Error>>(move error: Error): never =>
    core.error.throwing<Error>.raise(error)
}
