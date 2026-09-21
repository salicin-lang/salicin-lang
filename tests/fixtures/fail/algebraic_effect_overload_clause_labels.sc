let ask = effect {
  value: (left: i32): i32
  value: (right: i32): i32
}

let main = {
  (): i32 =>
  ask.handle(do {
    ask.value(left: 42)
  }) {
    value(input, resume) => do { resume(input) },
  }
}
