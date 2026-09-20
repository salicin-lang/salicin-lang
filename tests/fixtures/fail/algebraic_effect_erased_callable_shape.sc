let ask = effect {
  value: (): i32
}

let apply = { with<ask>(action: with<ask>(): i32): i32 =>
  action()
}

let run = { (move action: with<ask>(): i32): i32 =>
  ask.handle(do {
      apply(action)
    }) {
    value(resume) => do { resume(42) },
  }
}

let main = { (): i32 =>
  let action: with<ask>(): i32  = { () =>
    ask.value()
  }
  run(action)
}
