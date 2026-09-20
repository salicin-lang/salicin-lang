let read = effect {
  read: (): i32
}

let main = { (): i32 =>
  read.handle(do {
      let inner: i32 = read.handle(read.read()) {
        read(resume) => do { resume(2) },
      }
      inner + read.read()
    }) {
    read(resume) => do { resume(40) },
  }
}

test("algebraic_effect_nearest_handler.sc") {
  std.test.assert(main() == 42)
}
