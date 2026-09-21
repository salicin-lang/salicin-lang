let stop = effect {
  stop: (): bool
}

let main = {
  (): i32 =>
  stop.handle(do {
    let skipped = false && stop.stop()
    if(skipped) { 0 } else: { 42 }
  }) {
    stop(resume) => do { 1 },
  }
}

test("algebraic_effect_short_circuit.sc") {
  std.test.assert(main() == 42)
}
