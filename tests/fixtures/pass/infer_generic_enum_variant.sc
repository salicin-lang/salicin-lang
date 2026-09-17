let maybe<t: type> = enum {
  Some(t),
  None,
}

let main(): i32 = {
  let some = maybe.Some(42)
  let none: maybe(i32) = maybe.None
  let from_some = match some
    { Some(value) -> value }
    { None -> 0 }
  let from_none = match none
    { Some(value) -> value }
    { None -> 0 }
  from_some + from_none
}

test("infer_generic_enum_variant.sc") {
  std.test.assert(main() == 42)
}
