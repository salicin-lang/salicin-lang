let resource = struct { value: i32 }
let cell: <t: type> = struct { value: t }

extend(cell<t>)<requires: t is Copyable> {
  let duplicate = { (self: Borrow<self>)(): t => self.value }
}

let main = {
  (): i32 =>
  let cell = cell { value: resource { value: 42 } }
  cell.duplicate().value
}
