let Vec = alloc.Vec

let main = { (): i32 =>
  let mut values: Vec<i32> = Vec<i32>.new()
  let reference = values.at<mut>(0)
  reference
}

test("vec_at_access_mut_out_of_bounds.sc") {
  std.test.assert(main() == 42)
}
