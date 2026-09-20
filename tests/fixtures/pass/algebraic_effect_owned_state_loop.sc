let step = effect {
  delta (): i32
}

let state = struct {
  value: i32,
}

let program = { with<step>(): i32 =>
  let mut state = state { value: 40 }
  let mut count = 0
  while(count < 2) {
    let delta = step.delta()
    state.value = state.value + delta
    count = count + 1
  }
  state.value
}

let main = { (): i32 =>
  step.handle(do {
      program()
    }) {
    delta(resume) => do {
      resume(1)
    },
  }
}

test("algebraic_effect_owned_state_loop.sc") {
  std.test.assert(main() == 42)
}
