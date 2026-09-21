/// Type constructors whose payload can be transformed.
pub let Functor = trait<self: <Value: type>: type> {
  map<e: effects, A: type, B: type>with<e>(self: self<A>)(transform: with<e>(A) :B): self<B>;
}

/// Functors that can inject values and apply wrapped functions.
pub let Applicative = trait<self: <Value: type>: type><requires: self is Functor> {
  pure<A: type>(value: A): self<A>;

  apply<e: effects, A: type, B: type>with<e>(self: self<with<e>(A) :B>)(value: self<A>): self<B>;
}

/// Applicatives that can sequence dependent computations.
pub let Monad = trait<self: <Value: type>: type><requires: self is Applicative> {
  flat_map<e: effects, A: type, B: type>with<e>(self: self<A>)(next: with<e>(A) :self<B>): self<B>;
}

/// Implements `Functor` for `Option`.
extend<core.option.Option, Functor> {
  let map<e: effects, A: type, B: type> with<e>
    (self: core.Option<A>)
    (transform: with<e>(A) :B): core.Option<B> = {
    match(self) {
      Some(value) => core.Option.Some(transform(value)),
      None => core.Option.None,
    }
  }
}

/// Implements `Applicative` for `Option`.
extend<core.option.Option, Applicative> {
  let pure<A: type>
    (value: A): core.Option<A> = {
    core.Option.Some(value)
  }

  let apply<e: effects, A: type, B: type> with<e>
    (self: core.Option<with<e>(A) :B>)
    (value: core.Option<A>): core.Option<B> = {
    match(self) {
      Some(transform) => do {
        match(value) {
          Some(value) => core.Option.Some(transform(value)),
          None => core.Option.None,
        }
      },
      None => core.Option.None,
    }
  }
}

/// Implements `Monad` for `Option`.
extend<core.option.Option, Monad> {
  let flat_map<e: effects, A: type, B: type> with<e>
    (self: core.Option<A>)
    (next: with<e>(A) :core.Option<B>): core.Option<B> = {
    match(self) {
      Some(value) => next(value),
      None => core.Option.None,
    }
  }
}

/// Implements `Functor` for `Result<error>`.
extend<core.result.Result<Error>, Functor> {
  let map<e: effects, A: type, B: type> with<e>
    (self: core.Result<Error><A>)
    (transform: with<e>(A) :B): core.Result<Error><B> = {
    match(self) {
      Ok(value) => core.Result.Ok(transform(value)),
      Err(error) => core.Result.Err(error),
    }
  }
}

/// Implements `Applicative` for `Result<error>`.
extend<core.result.Result<Error>, Applicative> {
  let pure<A: type>
    (value: A): core.Result<Error><A> = {
    core.Result.Ok(value)
  }

  let apply<e: effects, A: type, B: type> with<e>
    (self: core.Result<Error><with<e>(A) :B>)
    (value: core.Result<Error><A>): core.Result<Error><B> = {
    match(self) {
      Ok(transform) => do {
        match(value) {
          Ok(value) => core.Result.Ok(transform(value)),
          Err(error) => core.Result.Err(error),
        }
      },
      Err(error) => core.Result.Err(error),
    }
  }
}

/// Implements `Monad` for `Result<error>`.
extend<core.result.Result<Error>, Monad> {
  let flat_map<e: effects, A: type, B: type> with<e>
    (self: core.Result<Error><A>)
    (next: with<e>(A) :core.Result<Error><B>): core.Result<Error><B> = {
    match(self) {
      Ok(value) => next(value),
      Err(error) => core.Result.Err(error),
    }
  }
}
