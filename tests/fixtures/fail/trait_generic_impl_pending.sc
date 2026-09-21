let read = trait {
  read(self: Borrow<self>)(): i32
}

let cell<t: type> = struct { value: t }

extend<cell, read> {
  let read(self: Borrow<self>)(): i32 = { 0 }
}

let main(): i32 = { 0 }
