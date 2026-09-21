let step = effect {
  delta: (): i32
}

let update = { with<step>
  (value: Borrow<mut><i32>): () =>
  let delta = step.delta()
  value = value + delta
}

let program = { with<step>
  (index: usize): i32 =>
  let mut values = [40]
  update(values[index])
  values[0]
}

let main = {
  (): i32 =>
  step.handle(do {
    program(1)
  }) {
    delta(resume) => do {
      resume(2)
    },
  }
}

test("algebraic_effect_owned_index_oob.sc") {
  std.test.assert(main() == 42)
}
