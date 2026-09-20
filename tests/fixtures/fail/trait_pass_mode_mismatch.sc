let update = trait {
  let update = (self: Borrow<self>)(value: Borrow<i32>): i32
}

let number = struct { value: i32 }

extend(number, update) {
  let update = (self: Borrow<self>)(copy value: i32): i32 => { self.value }
}

let main = (): i32 => { 0 }
