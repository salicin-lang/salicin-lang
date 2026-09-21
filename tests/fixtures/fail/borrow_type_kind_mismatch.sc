let main = {
  (): i32 =>
  let value = 42
  let alias: Borrow<mut><i32> = borrow(value)
  alias
}
