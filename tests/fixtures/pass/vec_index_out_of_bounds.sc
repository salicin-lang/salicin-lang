let Vec = alloc.Vec

let main = { (): i32 =>
  let values = Vec.new<T: i32>()
  values[0]
}

test("vec_index_out_of_bounds.sc") {
  std.test.assert(main() == 42)
}
