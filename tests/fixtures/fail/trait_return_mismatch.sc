let check = trait {
  check: (self: Borrow<self>)(): i32
}

let number = struct { value: i32 }

extend(number, check) {
  let check = { (self: Borrow<self>)(): bool => true }
}

let main = { (): i32 => 0 }
