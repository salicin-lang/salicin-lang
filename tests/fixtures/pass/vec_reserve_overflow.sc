let Vec = alloc.Vec

let main: (): i32 = {
  let mut values: Vec<i32> = Vec<i32>.new()
  values.push(1)
  values.reserve(18446744073709551615)
  42
}

test("vec_reserve_overflow.sc") {
  std.test.assert(main() == 42)
}
