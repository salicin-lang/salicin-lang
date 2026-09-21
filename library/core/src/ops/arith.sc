/// Trait backing binary `+`.
pub let Add<Rhs: type> = trait {
  /// Result type produced by addition.
  Output: type
  /// Adds `rhs` to `self`.
  add(self)
  (rhs: Rhs): Output
}

/// Trait backing binary `-`.
pub let Sub<Rhs: type> = trait {
  /// Result type produced by subtraction.
  Output: type
  /// Subtracts `rhs` from `self`.
  sub(self)
  (rhs: Rhs): Output
}

/// Trait backing binary `*`.
pub let Mul<Rhs: type> = trait {
  /// Result type produced by multiplication.
  Output: type
  /// Multiplies `self` by `rhs`.
  mul(self)
  (rhs: Rhs): Output
}

/// Trait backing binary `/`.
pub let Div<Rhs: type> = trait {
  /// Result type produced by division.
  Output: type
  /// Divides `self` by `rhs`.
  div(self)
  (rhs: Rhs): Output
}

/// Trait backing binary `%`.
pub let Rem<Rhs: type> = trait {
  /// Result type produced by remainder.
  Output: type
  /// Computes the remainder of `self` divided by `rhs`.
  rem(self)
  (rhs: Rhs): Output
}

/// Trait backing unary numeric negation.
pub let Neg = trait {
  /// Result type produced by negation.
  Output: type
  /// Negates `self`.
  neg(self)(): Output
}
