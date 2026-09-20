let produce = trait {
  Item: type
  produce: (self: Borrow<self>)(): Item
}

let value = struct { value: i32 }

extend(value, produce) {
  let Item = i32;
  let produce = { (self: Borrow<self>)(): i32 => self.value }
}

let produce = { <t: type>(value: Borrow<t>): i32
requires(t is produce && t.Item == i32) => value.produce() }

let forward = { <t: type>(value: Borrow<t>): i32
requires(t is produce && t.Item == i32) => produce(value) }

let main = { (): i32 =>
  let value = value { value: 42 }
  forward(value)
}

test("where_associated_method.sc") {
  std.test.assert(main() == 42)
}
