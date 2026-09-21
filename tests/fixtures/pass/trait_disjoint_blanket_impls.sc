let convert: <to: type> = trait {
  convert: (self: Borrow<self>)(): to
}

let cell: <t: type> = struct { value: t }

extend(cell<t>, convert<i32>) {
  let convert = { (self: Borrow<self>)(): i32 => 42 }
}

extend(cell<t>, convert<i64>) {
  let convert = { (self: Borrow<self>)(): i64 => 42 }
}

let main = {
  (): i32 =>
  let cell = cell { value: true }
  42
}

test("trait_disjoint_blanket_impls.sc") {
  std.test.assert(main() == 42)
}
