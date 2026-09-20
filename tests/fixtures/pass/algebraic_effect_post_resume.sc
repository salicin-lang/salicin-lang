let read = effect {
  read: (): i32
}

let program = { with<read>(): i32 =>
  read.read()
}

let main = { (): i32 =>
  read.handle(do {
      program() + 1
    }) {
    read(resume) => do { resume(40) + 1 },
  }
}

test("algebraic_effect_post_resume.sc") {
  std.test.assert(main() == 42)
}
