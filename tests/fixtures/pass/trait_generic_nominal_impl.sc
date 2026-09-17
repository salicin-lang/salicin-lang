let read = trait {
  let read(self: Borrow<self>)(): i32
}

let cell<t: type> = struct { value: t }

extend(cell(i32), read) {
  let read(self: Borrow<self>)(): i32 = { self.value }
}

let main(): i32 = {
  let cell = cell(i32) { value: 42 }
  cell.read()
}

test("trait_generic_nominal_impl.sc") {
  std.test.assert(main() == 42)
}
