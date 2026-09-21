/// The normalized Result of interpreting one test registration.
pub let Outcome = enum {
  Passed,
  Failed(core.string.String),
}

/// Interprets exactly one unit-returning, String-throwing registration.
pub let run = { (
    move action: with<core.error.throwing<core.string.String>>() :(),
  ): Outcome =>
  core.error.throwing<core.string.String>.handle(action()) {
    raise(message) => Outcome.Failed(message),
    Return(_) => Outcome.Passed,
  }
}
