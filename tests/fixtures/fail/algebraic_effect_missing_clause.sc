let state = <s: type> effect {
  get (): s
  put (move value: s): ()
}

let main = { (): i32 =>
  state<i32>.handle(do {
      state<i32>.get()
    }) {
    get(resume) => do { resume(42) },
  }
}
