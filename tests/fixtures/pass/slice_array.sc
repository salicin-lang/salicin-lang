let Slice = core.memory.Slice

let read(value: Borrow<i32>): i32 = { value }

let main(): i32 = {
  let values = [20, 22, 0]
  let slice: Borrow<Slice<i32>> = borrow(values)
  let first = slice.at(0)
  let second = slice.at(1)
  if slice.len() == 3 {
    read(first) + read(second)
  } else {
    0
  }
}

test("slice_array.sc") {
  std.test.assert(main() == 42)
}
