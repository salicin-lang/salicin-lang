let main = {
  (): i32 =>
  let mut number = 42
  let reference: Borrow<i32> = do {
    let inner: Borrow<i32> = borrow(number)
    inner
  }
  number = 0
  reference
}
