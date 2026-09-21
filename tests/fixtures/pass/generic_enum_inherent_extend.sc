let maybe: <t: type> = enum {
  Some(t),
  None,
}

extend(maybe<t>) {
  let unwrap_or = {
    (move self)
    (move fallback: t): t =>
    match(self) { Some(value) => value, None => fallback,
    }
  }
}

let main = {
  (): i32 =>
  let value = maybe.Some(42)
  value.unwrap_or(0)
}

test("generic_enum_inherent_extend.sc") {
  std.test.assert(main() == 42)
}
