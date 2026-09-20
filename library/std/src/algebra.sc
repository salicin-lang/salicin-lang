/// Values with an associative combination operation.
pub let Semigroup = trait {
  combine: (move left: self, move right: self): self
}

/// A Semigroup with an identity value.
pub let Monoid = trait<requires: self is Semigroup> {
  empty: (): self
}
