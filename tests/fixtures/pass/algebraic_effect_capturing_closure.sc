let ask = effect {
  value (): i32
}

let invoke = { with<ask>(action: with<ask>(i32): i32)(input: i32): i32 =>
  action(input)
}

let main = { (): i32 =>
  ask.handle(do {
      let offset = 2
      let action: with<ask>(i32): i32  = { (input: i32) =>
        ask.value() + input + offset
      }
      invoke(action)(20)
    }) {
    value(resume) => do { resume(20) },
  }
}

test("algebraic_effect_capturing_closure.sc") {
  std.test.assert(main() == 42)
}
