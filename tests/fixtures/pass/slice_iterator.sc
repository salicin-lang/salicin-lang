let Slice = core.memory.Slice

let read = { (value: Borrow<i32>): i32 => value }

let main = {
  (): i32 =>
  let values: Array<i32><3> = [10, 11, 21]
  let slice: Borrow<Slice<i32>> = borrow(values)
  let mut total = 0
  for slice.iter() { value =>
    total = total + read(value)
  }
  total
}

test("slice_iterator.sc") {
  std.test.assert(main() == 42)
}
