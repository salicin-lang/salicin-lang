let ask = effect {
  value: (): i32
}

let state = struct {
  value: i32,
}

let run = {
  (state: Borrow<mut><state>)
  {move action: with<ask>(): i32}: i32 =>
  ask.handle(do {
    action() + state.value
  }) {
    value(resume) => do { resume(1) },
  }
}

let main = {
  (): i32 =>
  let mut state = state { value: 20 }
  run(state) {
    state.value = state.value + 1
    ask.value() + state.value
  }
}
