let read = trait {
  read(self: Borrow<self>)(): i32
}

let cell<t: type> = struct { value: t }

extend<cell<i32>, read> {
  let read(self: Borrow<self>)(): i32 = { self.value }
}

extend<cell<t>> {
  let take(move self)(): t = { self.value }
}

let main(): i32 = {
  let cell_value = cell<i32> { value: 42 }
  let read = cell.read(cell_value)()
  let taken = cell<i32>.take(cell_value)()
  read + taken - 42
}

test<"qualified_trait_generic_method.sc"> {
  std.test.assert(main() == 42)
}
