let combine = trait {
  combine: (self: Borrow<self>)(left: i32)(right: i32): i32
}

let number = struct { value: i32 }

extend(number, combine) {
  let combine = { (self: Borrow<self>)(left: i32, right: i32): i32 => left + right }
}

let main = { (): i32 => 0 }
