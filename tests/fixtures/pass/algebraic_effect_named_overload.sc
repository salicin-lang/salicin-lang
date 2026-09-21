let ask = effect {
  value: (left: i32): i32
  value: (right: i32): i32
}

let choose = { with<ask>
  (): i32 =>
  ask.value(left: 19) + ask.value(right: 23)
}

let main = {
  (): i32 =>
  ask.handle(do {
    choose()
  }) {
    value(left, resume) => do { resume(left) },
    value(right, resume) => do { resume(right) },
  }
}

test("algebraic_effect_named_overload.sc") {
  std.test.assert(main() == 42)
}
