let inspect: <a: access> = { (value: Borrow<a><i32>): i32 => value }

let cell: <t: type> = struct { value: t }

extend(cell<t>) {
  let view: <a: access> = { (self: Borrow<a><self>)(): Borrow<a><t> => borrow<a>(self.value) }
}

let main = {
  (): i32 =>
  let mut left = 1
  let right = 20
  let mut cell = cell { value: 20 }
  do {
    let reference = cell.view<mut>()
    reference = 22
  }
  let after = do {
    let reference = cell.view()
    reference
  }
  after + inspect(right) + inspect<mut>(left) - 1
}

test("access_generic.sc") {
  std.test.assert(main() == 42)
}
