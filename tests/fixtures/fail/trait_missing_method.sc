let read = trait {
  read: (self: Borrow<self>)(): i32
}

let number = struct { value: i32 }

extend(number, read) {
}

let main = { (): i32 => 0 }
