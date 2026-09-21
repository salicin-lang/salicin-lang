let tick = effect {
  tick: (): i32
}

let main = {
  (): i32 =>
  let mut count = 0
  tick.handle(do {
    while(count + tick.tick() <= 2) {
      count += 1
      if(count == 1) { continue() }
    }
    let stopped = loop {
      count += tick.tick()
      if(count == 3) { break(count) }
    }
    36 + count + stopped
  }) {
    tick(resume) => do { resume(1) },
  }
}

test("algebraic_effect_loops.sc") {
  std.test.assert(main() == 42)
}
