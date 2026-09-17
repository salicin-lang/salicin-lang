let Vec = alloc.Vec

let main(): i32 = {
  let mut values: Vec<i32> = Vec<i32>.new()
  values.replace(0)(42)
}

test("vec_replace_out_of_bounds.sc") {
  std.test.assert(main() == 42)
}
