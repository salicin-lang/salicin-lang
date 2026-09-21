/// Represents either a present value or the absence of one.
pub let Option = <T: type> enum {
  /// Contains a value of type `T`.
  Some(T),
  /// Contains no value.
  None,
}

/// Common inspection, borrowing, transformation, fallback, and conversion
/// operations for optional values.
extend(Option<T>) {
  /// Returns whether this Option contains a value.
  let is_some = {
    (self: Borrow<self>)
    (): bool =>
    match(self) { Some(_) => true, None => false,
    }
  }

  /// Returns whether this Option is empty.
  let is_none = {
    (self: Borrow<self>)
    (): bool =>
    match(self) { Some(_) => false, None => true,
    }
  }

  /// Projects this borrowed Option into an Option of a payload Borrow.
  let as_ref = { <a: access, r: region>
    (self: Borrow<a><r><self>)(): Option<Borrow<a><r><T>> =>
    match(self) { Some(value) => Option.Some(borrow<a>(value)), None => Option.None,
    }
  }

  /// Transforms `Some` once and preserves `None`.
  let map = { <e: effects, U: type>with<e>
    (move self)
    (move transform: with<e>(T) :U): Option<U> =>
    match(self) { Some(value) => Option.Some(transform(value)), None => Option.None,
    }
  }

  /// Runs `next` once for `Some` and preserves `None`.
  let and_then = { <e: effects, U: type>with<e>
    (move self)
    (move next: with<e>(T) :Option<U>): Option<U> =>
    match(self) { Some(value) => next(value), None => Option.None,
    }
  }

  /// Extracts `Some` or returns the eagerly evaluated fallback.
  let unwrap_or = {
    (move self)
    (move fallback: T): T =>
    match(self) { Some(value) => value, None => fallback,
    }
  }

  /// Extracts `Some` or evaluates `fallback` exactly once for `None`.
  let unwrap_or_else = { <e: effects>with<e>
    (move self)
    (move fallback: with<e>() :T): T =>
    match(self) { Some(value) => value, None => fallback(),
    }
  }

  /// Converts `Some` to `Ok` and `None` to the eagerly evaluated error.
  let ok_or = { <Error: type>
    (move self)
    (move error: Error): core.Result<Error><T> =>
    match(self) { Some(value) => core.Result.Ok(value), None => core.Result.Err(error),
    }
  }

  /// Converts `Some` to `Ok` and lazily constructs the error for `None`.
  let ok_or_else = { <e: effects, Error: type>with<e>
    (move self)
    (move error: with<e>() :Error): core.Result<Error><T> =>
    match(self) { Some(value) => core.Result.Ok(value), None => core.Result.Err(error()),
    }
  }
}

/// Provides `?.` chaining for `Option`.
extend(Option<T>, core.flow.Chain) {
  /// The payload type produced by a successful Option.
  let Item = T
  /// Rebuilds `Option` around a transformed payload type.
  let Rebind = Option

  /// Applies `transform` to `Some` and propagates `None`.
  let chain = { <e: effects, U: type>with<e>
    (self)
    (transform: with<e>(T) :U): Option<U> =>
    match(self) { Some(value) => Option.Some(transform(value)), None => Option.None,
    }
  }
}

/// Provides `??` fallback evaluation for `Option`.
extend(Option<T>, core.flow.Coalesce) {
  /// The value type returned by coalescing.
  let Item = T

  /// Extracts `Some` or evaluates `fallback` for `None`.
  let coalesce = { <e: effects>with<e>
    (self)
    (fallback: with<e>() :T): T =>
    match(self) { Some(value) => value, None => fallback(),
    }
  }
}

/// Provides postfix `!` extraction for `Option`.
extend(Option<T>, core.flow.Unwrap) {
  let Output = T

  let unwrap = {
    (move self): T =>
    match(self) {
      Some(value) => value, None => do {
        unsafe { raw_trap() }
      },
    }
  }
}
