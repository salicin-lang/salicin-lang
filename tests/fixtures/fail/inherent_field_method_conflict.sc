let a = struct { reset: i32 }

extend(a) {
  let reset(self: Borrow<self>)(): i32 = { self.reset }
}

let main(): i32 = { 0 }
