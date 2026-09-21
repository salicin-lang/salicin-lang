/// Trait backing Eq comparison.
pub let Eq: <Rhs: type> = trait {
  /// Returns whether `self` and `rhs` compare equal.
  eq: (self: Borrow<self>)
  (rhs: Borrow<Rhs>): bool
}

/// Four-way Result for partial comparison.
pub let PartialOrdering = enum {
  /// `self` is Less than the compared value.
  Less,
  /// The compared values are equivalent for ordering.
  Equal,
  /// `self` is Greater than the compared value.
  Greater,
  /// The values cannot be ordered relative to each other.
  Unordered,
}

/// Trait backing partial ordering comparisons.
pub let PartialOrd: <Rhs: type> = trait {
  /// Compares `self` with `rhs`, returning a partial ordering Result.
  partial_cmp: (self: Borrow<self>)
  (rhs: Borrow<Rhs>): PartialOrdering
}
