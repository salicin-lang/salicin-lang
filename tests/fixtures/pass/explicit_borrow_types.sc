let pair = struct { left: i32, right: i32 }
let cell = <t: type> struct { value: t }

let read = <t: type>(cell: Borrow<cell<t>>): t
requires(t is Copyable) => {
  let alias: Borrow<cell<t>> = borrow(cell)
  alias.value
}

let main = (): i32 => {
  let mut value = pair { left: 20, right: 2 }
  let before = do {
    let shared: Borrow<pair> = borrow(value)
    shared.left
  }
  let mutable: Borrow<mut><pair> = borrow<mut>(value)
  mutable.left = before + 20
  mutable.left + mutable.right + read(cell: cell { value: 1 }) - 1
}

test("explicit_borrow_types.sc") {
  std.test.assert(main() == 42)
}
