let cell = struct { value: i32 }

extend<cell> {
  let get<r: region>(self: Borrow<r><self>)(): Borrow<r><i32> = { borrow(self.value) }
}

let main(): i32 = {
  let reference = cell { value: 42 }.get()
  reference
}
