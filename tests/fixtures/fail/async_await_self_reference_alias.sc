let main = { (): i32 =>
  let future = async {
    let value = 41
    let first: Borrow<i32> = borrow(value)
    let second: Borrow<i32> = first
    let awaited = await(child())
    second + awaited
  }
  0
}

let child = { () =>
  async { 1 }
}
