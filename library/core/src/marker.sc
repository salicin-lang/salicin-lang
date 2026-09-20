/// Auto marker for types whose owning value may be safely relocated.
pub let Movable = trait {}

/// Marker trait for types that may be duplicated by implicit copy.
pub let Copyable = trait<requires: self is Movable> {}

/// Trait for types that need cleanup when their owning value leaves scope.
pub let Droppable = trait {
  /// Releases resources owned by `self`.
  let drop = (self: Borrow<mut><self>)
    (): ()
}
