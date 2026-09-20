let ask = effect {
  value (): i32
}

let ask = { with<ask>(): i32 =>
  ask.value()
}

let main = { (): i32 =>
  ask.handle(do {
      let mut action = ask
      action()
    }) {
    value(resume) => do { resume(42) },
  }
}
