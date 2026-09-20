let number = struct { value: i32 }
let other = struct { value: i32 }

extend(number) {
  let read = { (self: Borrow<self>)(): i32 => self.value }
}

extend(other) {
  let read = { (self: Borrow<self>)(): i32 => self.value }
}

let main = { (): i32 => number.read(other{ value: 42 })() }
