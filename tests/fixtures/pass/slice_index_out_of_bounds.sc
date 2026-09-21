let Slice = core.memory.Slice

let main: (): i32 = {
  let values = [1, 2]
  let slice: Borrow<Slice<i32>> = borrow(values)
  slice[2]
}

test<"slice_index_out_of_bounds.sc"> {
  std.test.assert(main() == 42)
}
