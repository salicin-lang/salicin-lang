let abort = effect {
  stop: (value: i32): never
}

let fail = { with<abort>(): never =>
  abort.stop(42)
}

let main = { (): i32 =>
  abort.handle(do {
      fail()
    }) {
    stop(value) => do { value },
  }
}

test("algebraic_effect_never_abort.sc") {
  std.test.assert(main() == 42)
}
