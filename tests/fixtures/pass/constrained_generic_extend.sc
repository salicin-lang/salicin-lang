let cell: <t: type> = struct { value: t }

extend(cell<t>)<requires: t is Copyable> {
  let new = { (copy value: t): cell<t> => cell { value: value } }
  let duplicate = {
    (self: Borrow<self>)
    (): t =>
    let first = self.value
    self.value
  }
}

let read_twice: <t: type> = { (cell: Borrow<cell<t>>): t requires(t is Copyable) =>
  cell.duplicate()
}

let main = {
  (): i32 =>
  let cell = cell.new(42)
  read_twice(cell)
}

test("constrained_generic_extend.sc") {
  std.test.assert(main() == 42)
}
