let view = <t: type><a: access><r: region>: type Borrow<a><r><t>;

let lend = trait {
  let Item = <a: access><r: region>: type

  let view = <a: access, r: region>
    (self: Borrow<a><r><self>)(): Item<a><r>
  }

let cell = struct { value: i32 }

extend(cell, lend) {
  let Item = view<i32>;

  let view = <a: access, r: region>
    (self: Borrow<a><r><self>)(): Borrow<a><r><i32> => {
    borrow<a>(self.value)
  }
}

let read = (value: Borrow<i32>): i32 => { value }

let main = (): i32 => {
  let mut cell = cell { value: 40 }
  let before = do {
    let value = cell.view<shared>()
    read(value)
  }
  do {
    let value = cell.view<mut>()
    value = before + 2
  }
  let final_value = cell.view<shared>()
  read(final_value)
}

test("gat_borrow_family.sc") {
  std.test.assert(main() == 42)
}
