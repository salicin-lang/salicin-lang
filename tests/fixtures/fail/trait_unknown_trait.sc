let number = struct { value: i32 }

extend(number, missing_trait) {
  let read: (self: Borrow<self>)(): i32 = { self.value }
}

let main: (): i32 = { 0 }
