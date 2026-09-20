let read = trait {
  let read = { (self: Borrow<self>)(): i32 }
}

let cell = <t: type> struct { value: t }

extend(cell<t>, read) {
  let read = { (self: Borrow<self>)(): i32 => missing }
}

let main = { (): i32 => 42 }
