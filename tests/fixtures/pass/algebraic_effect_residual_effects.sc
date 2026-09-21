let Result = core.Result

let throwing = core.error.throwing
let unsafety = core.unsafe.unsafety

let supply = effect {
  seed: (): i32
}

let ask = effect {
  value: with<supply, throwing<bool>, unsafety>(): i32
}

let request = { with<ask, supply, throwing<bool>, unsafety>
  (): i32 =>
  ask.value()
}

let run = { with<supply, throwing<bool>>(): i32 =>
  unsafe {
    ask.handle(do {
      request()
    }) {
      value(resume) => do { resume(42) },
    }
  }
}

let main = {
  (): i32 =>
  let result: Result<bool><i32> = try {
    supply.handle(do { run() }) {
      seed(resume) => do { resume(0) },
    }
  }
  result ?? 0
}

test("algebraic_effect_residual_effects.sc") {
  std.test.assert(main() == 42)
}
