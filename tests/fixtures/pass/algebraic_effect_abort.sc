let abort = effect {
  stop: (): i32
}

let main = { (): i32 =>
  let mut reached = 0
  let result = abort.handle(do {
      let value = abort.stop()
      reached = 1;
      value
    }) {
    stop(resume) => do { 42 },
  }
  result + reached
}

test("algebraic_effect_abort.sc") {
  std.test.assert(main() == 42)
}
