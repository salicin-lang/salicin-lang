let read = trait {
  let read = (self: Borrow<self>)(): i32
}

let number = struct { value: i32 }

extend(number, read) {
  let read = (self: Borrow<self>)(): i32 => { self.value }
}

extend(number, read) {
  let read = (self: Borrow<self>)(): i32 => { self.value }
}

let main = (): i32 => { 0 }
