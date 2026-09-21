let ask = effect {
  value: (): i32
}

let leak: with<ask>
  (value: Borrow<mut><i32>): (with<ask>(): i32) = {
  let mut action: with<ask>(): i32  = { () =>
    value = value + 1
    ask.value() + value
  }
  action
}

let main: (): i32 = {
  42
}
