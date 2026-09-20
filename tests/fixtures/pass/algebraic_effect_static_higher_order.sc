let ask = effect {
  value (): i32
}

let ask = { with<ask>(): i32 =>
  ask.value()
}

let invoke = { with<ask>(action: with<ask>(): i32): i32 =>
  action()
}

let main = { (): i32 =>
  ask.handle(do {
      let selected = ask
      invoke(selected)
    }) {
    value(resume) => do { resume(42) },
  }
}

test("algebraic_effect_static_higher_order.sc") {
  std.test.assert(main() == 42)
}
