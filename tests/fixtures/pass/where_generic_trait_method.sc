let convert<to: type> = trait {
  let convert(self: Borrow<self>)(): to
}

let value = struct { value: i32 }

extend(value, convert<i32>) {
  let convert(self: Borrow<self>)(): i32 = { self.value }
}

let convert<t: type>(value: Borrow<t>): i32
= requires(t is convert<i32>) { value.convert() }

let main(): i32 = {
  let value = value{ value: 42 }
  convert(value)
}

test("where_generic_trait_method.sc") {
  std.test.assert(main() == 42)
}
