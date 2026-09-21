let maybe: <t: type> = enum {
  Some(t),
  None,
}

let unwrap = {
  (move value: maybe<i32>): i32 =>
  match(value) { Some(item) => item, None => 0,
  }
}

let main = { (): i32 => unwrap(maybe<i32>.Some(42)) }

test("generic_enum_match.sc") {
  std.test.assert(main() == 42)
}
