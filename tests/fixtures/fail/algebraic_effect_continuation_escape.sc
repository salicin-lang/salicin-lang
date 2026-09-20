let ask = effect {
  value (): i32
}

let main = { (): i32 =>
  ask.handle(do {
      ask.value()
    }) {
    value(resume) => do {
      let escaped = resume
      42
    },
  }
}
