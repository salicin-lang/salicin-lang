let probe = effect {
  read (): bool
}

let main = { (): i32 =>
  probe.handle(do {
      probe.read()
    }) {
    read(resume) => do { resume(true) },
    Return(value) => do {
      if(value) { 42 } else: { 0 }
    },
  }
}

test("algebraic_effect_done.sc") {
  std.test.assert(main() == 42)
}
