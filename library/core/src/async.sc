/// Internal suspension effect discharged by compiler-generated futures.
pub let suspension = effect {
  /// Suspends the current asynchronous computation.
  suspend(): ()
}

/// Result of polling an asynchronous computation once.
pub let Poll<T: type> = enum {
  Pending,
  Ready(T)
}

/// A cold asynchronous computation with residual effect row `E`.
pub let Future<e: effects> = trait<requires: self is Movable> {
  Output: type

  poll<r: region>with<e>(self: Borrow<mut><r><self>)(): Poll<Output>
  }

/// Explicit executor protocol. Creating a future never selects an executor.
pub let Executor = trait {
  run<e: effects, F: type, T: type>with<e>(self: Borrow<mut><self>)(move future: F): T requires<F is Future<e> && F.Output == T>
  }

/// Constructs a cold compiler-generated future without running `action`.
pub let async<e: effects, F: type, T: type>{move action: with<core.async.suspension, e>() :T}: F requires<F is Future<e> && F.Output == T> = builtin()

/// Suspends the enclosing async computation until `future` is Ready.
pub let await<e: effects, F: type, T: type> with<core.async.suspension, e>
  (move future: F): T requires<F is Future<e> && F.Output == T> = {
  let mut current = future
  loop {
    match(current.poll()) {
      Pending => suspension.suspend(),
      Ready(value) => break(value),
    }
  }
}
