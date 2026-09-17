let read<r: region>(value: Borrow<r><i32>): i32 = {
  let alias: Borrow<r><i32> = borrow(value)
  alias
}

let generic_read<r: region, t: type>(cell: Borrow<r><cell<t>>): t
= requires(t is Copyable) {
  let alias: Borrow<r><cell<t>> = borrow(cell)
  alias.value
}

let cell<t: type> = struct { value: t }

let main(): i32 = {
  let value = 20
  read(value) + generic_read(cell: cell{ value: 22 })
}

test("region_scoped_borrow.sc") {
  std.test.assert(main() == 42)
}
