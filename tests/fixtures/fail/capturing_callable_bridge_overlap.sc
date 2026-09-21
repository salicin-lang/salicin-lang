let overlap: (move action: (): i32)
  (value: Borrow<mut><i32>): i32 = {
  action() + *value
}

let main: (): i32 = {
  let mut value = 20
  overlap({ () =>
    value = value + 1;
    value
  })(borrow<mut>(value))
}
