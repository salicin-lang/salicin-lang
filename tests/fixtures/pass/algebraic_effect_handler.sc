let state = <s: type> effect {
  get: (): s
  put: (move value: s): ()
}

let add_two = { (value: i32): i32 => value + 2 }

let main = { (): i32 =>
  let mut state_value = 40
  state<i32>.handle(do {
      state<i32>.put(add_two(state<i32>.get()))
      state<i32>.get()
    }) {
    get(resume) => do { resume(state_value) },
    put(value, resume) => do {
      state_value = value;
      resume(())
    },
  }
}

test("algebraic_effect_handler.sc") {
  std.test.assert(main() == 42)
}
