let read = effect {
  read (): i32
}

let add = effect {
  add (x: i32): i32
}

let program = { with<read, add>(): i32 =>
  add.add(read.read())
}

let main = { (): i32 =>
  read.handle(do {
      add.handle(do {
          program()
        }) {
        add(x, resume) => do { resume(x + read.read() + 2) },
      }
    }) {
    read(resume) => do { resume(20) },
  }
}

test("algebraic_effect_composition.sc") {
  std.test.assert(main() == 42)
}
