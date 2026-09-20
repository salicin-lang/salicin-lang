let ask = effect {
  value: (): i32
}

let ask = { with<ask>(): i32 =>
  ask.value()
}

let leak = { with<ask>(): (with<ask>(): i32) =>
  ask.handle(do {
      let action = ask
      action
    }) {
    value(resume) => do { resume(42) },
  }
}

let main = { (): i32 => 0 }
