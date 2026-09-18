let read = trait {
  let read(self: Borrow<self>)(): i32
}

let leaf = struct { value: i32 }

extend(leaf, read) {
  let read(self: Borrow<self>)(): i32 = { self.value }
}

let cell<t: type> = struct { value: t }

extend(cell<t>, read)<requires: t is read> {
  let read(self: Borrow<self>)(): i32 = { self.value.read() }
}

let read_cell<t: type>(cell: Borrow<cell<t>>): i32
  = requires(t is read) { cell.read() }

let value = trait {
  let Item: type
  let take(move self)(): Item
}

extend(cell<t>, value) {
  let Item = t;
  let take(move self)(): t = { self.value }
}

let main(): i32 = {
  let cell = cell{ value: leaf{ value: 42 } }
  let read = read_cell(cell)
  let leaf = cell.take()
  let wrapped = cell{ value: leaf }
  wrapped.read() + read - 42
}

test("trait_generic_blanket_impl.sc") {
  std.test.assert(main() == 42)
}
