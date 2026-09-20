let identity = <t: type>: type t

let factory = trait {
  let Item = <t: type>: type

  let make = { (self: Borrow<self>)(value: i32): Item<i32> }
  }

let cell = struct {}

extend(cell, factory) {
  let Item = identity;

  let make = { (self: Borrow<self>)(value: i32): i32 => value }
}

let make_i32 = { <t: type>(value: Borrow<t>): i32
requires(t is factory && t.Item<u: type> == u) =>
  value.make(42)
}

let main = { (): i32 =>
  let cell = cell {}
  make_i32(cell)
}

test("where_gat_equality.sc") {
  std.test.assert(main() == 42)
}
