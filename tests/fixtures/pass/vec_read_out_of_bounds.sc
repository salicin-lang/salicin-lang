let Vec = alloc.Vec

let main = (): i32 => {
  let values: Vec<i32> = Vec<i32>.new()
  values.read(0)
}

test("vec_read_out_of_bounds.sc") {
  std.test.assert(main() == 42)
}
