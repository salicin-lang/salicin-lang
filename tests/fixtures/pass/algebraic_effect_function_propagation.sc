let state = <s: type> effect {
  get: (): s
}

let program = { with<state<i32>>(): i32 =>
  let answer = 1
  state<i32>.get() + answer
}

let main = {
  (): i32 =>
  let answer = 40
  state<i32>.handle(do {
    program() + 1
  }) {
    get(resume) => do { resume(answer) },
  }
}

test("algebraic_effect_function_propagation.sc") {
  std.test.assert(main() == 42)
}
