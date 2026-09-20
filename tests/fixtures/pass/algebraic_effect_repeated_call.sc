let read = effect {
  read: (value: i32): i32
}

let once = { with<read>(value: i32): i32 =>
  read.read(value)
}

let main = { (): i32 =>
  read.handle(do {
      once(19) + once(23)
    }) {
    read(value, resume) => do { resume(value) },
  }
}

test("algebraic_effect_repeated_call.sc") {
  std.test.assert(main() == 42)
}
