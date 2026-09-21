let main: (): i32 = {
  let value = 42
  let alias: Borrow<i64> = borrow(value)
  0
}
