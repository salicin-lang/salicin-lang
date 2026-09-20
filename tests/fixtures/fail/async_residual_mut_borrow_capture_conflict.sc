let ask = effect {
  ask: (): i32
}

let program = { (value: Borrow<mut><i32>): i32 =>
  let future = async {
    value = value + ask.ask()
    value
  }
  value = 0
  42
}

let main = { (): i32 =>
  let mut value = 2
  program(value)
}
