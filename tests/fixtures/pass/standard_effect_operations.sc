let Result = core.Result

let throwing = core.error.throwing
let suspension = core.async.suspension

let fail_with_answer = { with<throwing<i32>>(): never =>
  throwing<i32>.raise(42)
}

let fail_with_throw_sugar = { with<throwing<i32>>(): never =>
  throw(42)
}

let choose_with_throw_sugar = { with<throwing<i32>>(fail: bool): i32 =>
  if(fail) { throw(42) } else: { 1 }
}

let handled_throw = {
  (): i32 =>
  throwing<i32>.handle(do {
    fail_with_answer()
  }) {
    raise(error) => do { error },
  }
}

let handled_throw_sugar_function = {
  (): i32 =>
  throwing<i32>.handle(do {
    fail_with_throw_sugar()
  }) {
    raise(error) => do { error },
  }
}

let handled_throw_sugar_action = {
  (): i32 =>
  throwing<i32>.handle(do {
    throw(42)
  }) {
    raise(error) => do { error },
  }
}

let tried_throw_sugar_function = {
  (): i32 =>
  let result: Result<i32><i32> = try {
    fail_with_throw_sugar()
  }
  match(result) { Ok(value) => value, Err(error) => error,
  }
}

let tried_throw_sugar_action = {
  (): i32 =>
  let result: Result<i32><i32> = try {
    throw(42)
  }
  match(result) { Ok(value) => value, Err(error) => error,
  }
}

let inferred_try_from_throw_sugar_function = {
  (): i32 =>
  let result = try {
    choose_with_throw_sugar(true)
  }
  match(result) { Ok(value) => value, Err(error) => error,
  }
}

let handled_async = {
  (): i32 =>
  let mut seen = 0
  let value = suspension.handle(do {
    suspension.suspend();
    1
  }) {
    suspend(resume) => do {
      seen = 1;
      resume(())
    },
  }
  value + seen + 40
}

let main = {
  (): i32 =>
  handled_throw() + handled_throw_sugar_function() + handled_throw_sugar_action() + tried_throw_sugar_function() + tried_throw_sugar_action() + inferred_try_from_throw_sugar_function() + handled_async() - 252
}

test("standard_effect_operations.sc") {
  std.test.assert(main() == 42)
}
