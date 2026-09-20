let Slice = core.memory.Slice

let main = { (): i32 =>
  let mut values = [20, 22]
  let slice: Borrow<Slice<i32>> = borrow(values)
  values[0] = 0
  Slice.at(1)
}
