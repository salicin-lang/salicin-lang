/// Trait backing unary logical or bitwise not.
pub let Not = trait {
  /// Result type produced by not.
  Output: type
  /// Inverts `self`.
  not: (self)(): Output
}

/// Trait backing binary `&`.
pub let BitAnd = <Rhs: type> trait {
  /// Result type produced by bitwise and.
  Output: type
  /// Computes bitwise and with `rhs`.
  bit_and: (self)
  (rhs: Rhs): Output
}

/// Trait backing binary `|`.
pub let BitOr = <Rhs: type> trait {
  /// Result type produced by bitwise or.
  Output: type
  /// Computes bitwise or with `rhs`.
  bit_or: (self)
  (rhs: Rhs): Output
}

/// Trait backing binary `^`.
pub let BitXor = <Rhs: type> trait {
  /// Result type produced by bitwise xor.
  Output: type
  /// Computes bitwise xor with `rhs`.
  bit_xor: (self)
  (rhs: Rhs): Output
}

/// Trait backing binary `<<`.
pub let Shl = <Rhs: type> trait {
  /// Result type produced by left shift.
  Output: type
  /// Shifts `self` left by `rhs`.
  shl: (self)
  (rhs: Rhs): Output
}

/// Trait backing binary `>>`.
pub let Shr = <Rhs: type> trait {
  /// Result type produced by right shift.
  Output: type
  /// Shifts `self` right by `rhs`.
  shr: (self)
  (rhs: Rhs): Output
}
