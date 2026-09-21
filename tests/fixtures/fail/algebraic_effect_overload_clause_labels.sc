let ask = effect {
  value: (left: i32): i32
  value: (right: i32): i32
}

let main: (): i32 = {
  ask.handle {
    value: { (input, resume) => resume(input) },
    action: { ask.value(left: 42) },
  }
}
