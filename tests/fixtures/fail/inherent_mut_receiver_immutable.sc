let counter = struct { value: i32 }

extend<counter> {
  let reset(self: Borrow<mut><self>)
    (): () = {
    self.value = 0
  }
}

let main(): i32 = {
  let counter = counter { value: 42 }
  counter.reset()
  0
}
