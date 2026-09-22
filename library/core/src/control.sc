// Control syntax uses declaration-directed Brace groups and targets these
// validated functions. Most control helpers are ordinary source definitions;
// the compiler only keeps syntax-directed shortcuts and the few places that
// need authority or primitive control-flow lowering.
/// Dynamically exits the nearest loop whose Result type is `T`.
pub let loop_exit<T: type> = effect {
  exit(move value: T): never
}

/// Dynamically starts the next iteration of the nearest loop.
pub let iteration_skip = effect {
  next(): never
}

/// Dynamically returns from the nearest function boundary returning `T`.
pub let function_exit<T: type> = effect {
  exit(move value: T): never
}

/// The observable Result of trying one refutable pattern function.
pub let Attempt<Input: type><Output: type> = enum {
  Hit(Output),
  Miss(Input),
}

pub let break<T: type> with<loop_exit<T>>
  (move value: T): never = {
  loop_exit<T>.exit(value)
}

pub let break: with<loop_exit<()>>(): never = {
  loop_exit<()>.exit(())
}

pub let continue: with<iteration_skip>
  (): never = {
  iteration_skip.next()
}

pub let return<T: type> with<function_exit<T>>
  (move value: T): never = {
  function_exit<T>.exit(value)
}

pub let return: with<function_exit<()>>(): never = {
  function_exit<()>.exit(())
}

/// Runs `action` and preserves its effect row.
pub let do<e: effects, T: type> with<e>
  {move action: with<e>() :T}: T = {
  action()
}

/// Registers `action` to run when the current lexical scope exits.
pub let defer<e: effects> with<e>{move action: with<e>() :()}: () = builtin()

/// Runs `action` once, then repeats it while the lazy condition remains true.
pub let do<e: effects> with<e>
  {move action: with<core.control.loop_exit<()>, core.control.iteration_skip, e>() :()}
  {move condition: with<core.control.loop_exit<()>, core.control.iteration_skip, e>() :bool}: () = {
  loop {
    core.control.iteration_skip.handle {
      next: { () => () },
      action: { action() },
    }
    if(condition()) {
      continue()
    } else: {
      break()
    }
  }
}

/// Repeats `body` indefinitely until control exits through another construct.
pub let loop<e: effects, T: type> with<e>{move body: with<core.control.loop_exit<T>, core.control.iteration_skip, e>() :()}: T = builtin()

/// Repeats `body` while the lazy condition remains true.
pub let while<e: effects> with<e>
  (move condition: with<e>(): bool)
  {move do: with<e>(): ()}: () = {
  loop {
    if(condition()) {
      do()
    } else: {
      break()
    }
  }
}

/// Selects one of two lazy branches from an eager boolean condition.
pub let if<e: effects, T: type> with<e>
  (condition: bool)
  {move then: with<e>(): T}
  {move else: with<e>(): T}: T = {
  match(condition) {
    true => then(),
    false => else(),
  }
}

/// Selects the first matching case parameter group.
pub let match<
  Input: type,
  Output: type,
  e: effects,
  ...cases: parameters,
> with<e>
  (move input: Input)
  ...cases: Output = builtin()

/// Iterates through `iterable`, passing each item to the lazy body.
pub let for<e: effects, Iterable: type, Iter: type, Item: type> with<e>
  (move iterable: Iterable)
  {move body: with<core.control.loop_exit<()>, core.control.iteration_skip, e>(Item): ()}: () requires<
  Iterable is core.iter.IntoIterator &&
  Iterable.Iter == Iter &&
  Iter is core.iter.Iterator &&
  Iter.Item == Item
> = {
  let mut iterator = iterable.into_iter()
  loop {
    match(iterator.next()) {
      Some(item) => body(item),
      None => break(),
    }
  }
}
