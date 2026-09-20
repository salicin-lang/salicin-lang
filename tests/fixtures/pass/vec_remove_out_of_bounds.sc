let Vec = alloc.Vec

let main = { (): i32 =>
  let mut values: Vec<i32> = Vec<i32>.new()
  values.remove(0)
}

test("vec_remove_out_of_bounds.sc") {
  std.test.assert(main() == 42)
}
