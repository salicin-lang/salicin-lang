let ask = effect {
  value (): i32
}

let ask = { with<ask>(): i32 =>
  ask.value()
}

let main = { (): i32 =>
  ask.handle(do {
      let action = ask
      let forwarded = action
      forwarded()
    }) {
    value(resume) => do { resume(42) },
  }
}

test("algebraic_effect_function_alias.sc") {
  std.test.assert(main() == 42)
}
