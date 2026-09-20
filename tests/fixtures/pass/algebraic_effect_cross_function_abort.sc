let stop = effect {
  stop (): i32
}

let program = { with<stop>(): i32 =>
  let value = stop.stop()
  value + 1
}

let main = { (): i32 =>
  let result = stop.handle(do {
      program() + 1
    }) {
    stop(resume) => do { 40 },
  }
  result + 2
}

test("algebraic_effect_cross_function_abort.sc") {
  std.test.assert(main() == 42)
}
