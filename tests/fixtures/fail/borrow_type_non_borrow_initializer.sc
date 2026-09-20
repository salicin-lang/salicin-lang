let main = (): i32 => {
  let value = 42
  let alias: Borrow<i32> = value
  alias
}
