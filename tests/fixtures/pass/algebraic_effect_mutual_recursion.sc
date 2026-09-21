let tick = effect {
  tick: (): i32
}

let even = { with<tick>
  (count: i32): i32 =>
  if(count == 0) { return(0) }
  tick.tick() + odd(count - 1)
}

let odd = { with<tick>
  (count: i32): i32 =>
  if(count == 0) { return(0) }
  tick.tick() + even(count - 1)
}

let main = {
  (): i32 =>
  let value = 14
  tick.handle(do {
    even(3)
  }) {
    tick(resume) => do { resume(value) },
  }
}

test("algebraic_effect_mutual_recursion.sc") {
  std.test.assert(main() == 42)
}
