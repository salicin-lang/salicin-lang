/// Authority effect required for operations that can violate language safety.
pub let unsafety = effect {}

/// Runs an action that requires the unsafe authority effect.
pub let unsafe = { <e: effects, T: type>with<e>{move action: with<core.unsafe.unsafety, e>() :T}: T =>
    core.unsafe.unsafety.handle(action()) {
  }
}
