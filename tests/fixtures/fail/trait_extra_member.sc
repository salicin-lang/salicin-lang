let read = trait {
  read: (self: Borrow<self>)(): i32
}

let number = struct { value: i32 }

extend(number, read) {
  let read = { (self: Borrow<self>)(): i32 => self.value }
  let extra = { (self: Borrow<self>)(): i32 => 0 }
}

let main = { (): i32 => 0 }
