let ask = effect {
  value (): i32
}

let apply_twice = { with<ask>(move action: with<ask>(): i32): i32 =>
  action() + action()
}

let run = { (move action: with<ask>(): i32): i32 =>
  ask.handle(do {
      apply_twice(action)
    }) {
    value(resume) => do { resume(21) },
  }
}

let main = { (): i32 =>
  let action: with<ask>(): i32  = { () =>
    ask.value()
  }
  run(action)
}
