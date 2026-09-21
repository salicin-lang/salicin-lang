/// Represents either a successful value or an error payload.
pub let Result: <Error: type>
  <T: type> = enum {
  /// Contains the successful value.
  Ok(T),
  /// Contains the error value.
  Err(Error),
}

/// Common inspection, borrowing, transformation, fallback, and projection
/// operations for success-or-error values.
extend(Result<Error><T>) {
  /// Returns whether this Result contains a success value.
  let is_ok = {
    (self: Borrow<self>)
    (): bool =>
    match(self) { Ok(_) => true, Err(_) => false,
    }
  }

  /// Returns whether this Result contains an error.
  let is_err = {
    (self: Borrow<self>)
    (): bool =>
    match(self) { Ok(_) => false, Err(_) => true,
    }
  }

  /// Projects this borrowed Result into borrowed success and error payloads.
  let as_ref: <a: access, r: region> = { (self: Borrow<a><r><self>)
    (): Result<Borrow<a><r><Error>><Borrow<a><r><T>> =>
    match(self) { Ok(value) => Result.Ok(borrow<a>(value)), Err(error) => Result.Err(borrow<a>(error)),
    }
  }

  /// Transforms `Ok` once and preserves `Err`.
  let map: <e: effects, U: type> = { with<e>
    (move self)
    (move transform: with<e>(T) :U): Result<Error><U> =>
    match(self) { Ok(value) => Result.Ok(transform(value)), Err(error) => Result.Err(error),
    }
  }

  /// Transforms `Err` once and preserves `Ok`.
  let map_error: <e: effects, MappedError: type> = { with<e>
    (move self)
    (move transform: with<e>(Error) :MappedError): Result<MappedError><T> =>
    match(self) { Ok(value) => Result.Ok(value), Err(error) => Result.Err(transform(error)),
    }
  }

  /// Runs `next` once for `Ok` and preserves `Err`.
  let and_then: <e: effects, U: type> = { with<e>
    (move self)
    (move next: with<e>(T) :Result<Error><U>): Result<Error><U> =>
    match(self) { Ok(value) => next(value), Err(error) => Result.Err(error),
    }
  }

  /// Extracts `Ok` or returns the eagerly evaluated fallback.
  let unwrap_or = {
    (move self)
    (move fallback: T): T =>
    match(self) { Ok(value) => value, Err(_) => fallback,
    }
  }

  /// Extracts `Ok` or evaluates `fallback` exactly once for `Err`.
  let unwrap_or_else: <e: effects> = { with<e>
    (move self)
    (move fallback: with<e>(Error) :T): T =>
    match(self) { Ok(value) => value, Err(error) => fallback(error),
    }
  }

  /// Converts `Ok` to `Some` and `Err` to `None`.
  let ok = {
    (move self)
    (): core.Option<T> =>
    match(self) { Ok(value) => core.Option.Some(value), Err(_) => core.Option.None,
    }
  }

  /// Converts `Err` to `Some` and `Ok` to `None`.
  let err = {
    (move self)
    (): core.Option<Error> =>
    match(self) { Ok(_) => core.Option.None, Err(error) => core.Option.Some(error),
    }
  }
}

/// Provides `?.` chaining for `Result`.
extend(Result<Error><T>, core.flow.Chain) {
  /// The success payload type.
  let Item = T
  /// Rebuilds `Result<Error>` around a transformed success type.
  let Rebind = Result<Error>;

  /// Applies `transform` to `Ok` and propagates `Err`.
  let chain: <e: effects, U: type> = { with<e>
    (self)
    (transform: with<e>(T) :U): Result<Error><U> =>
    match(self) { Ok(value) => Result.Ok(transform(value)), Err(error) => Result.Err(error),
    }
  }
}

/// Provides `??` fallback evaluation for `Result`.
extend(Result<Error><T>, core.flow.Coalesce) {
  /// The success payload type returned by coalescing.
  let Item = T

  /// Extracts `Ok` or evaluates `fallback` for `Err`.
  let coalesce: <e: effects> = { with<e>
    (self)
    (fallback: with<e>() :T): T =>
    match(self) { Ok(value) => value, Err(_) => fallback(),
    }
  }
}

/// Provides postfix `!` extraction for `Result`.
extend(Result<Error><T>, core.flow.Unwrap) {
  let Output = T

  let unwrap = {
    (move self): T =>
    match(self) {
      Ok(value) => value, Err(_) => do {
        unsafe { raw_trap() }
      },
    }
  }
}

/// Provides postfix `!` effect raising for `Result`.
extend(Result<E><T>, core.flow.Raise) {
  let Output = T
  let Error = E

  let raise = { with<core.error.throwing<E>>(move self): T =>
    match(self) { Ok(value) => value, Err(error) => core.error.throw(error),
    }
  }
}
