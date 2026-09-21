let resource = struct { value: i32 }
let wrapper = struct { resource: resource }
let choice = enum {
  Some(wrapper),
  None,
}

extend(resource, Droppable) {
  let drop: (self: Borrow<mut><self>)
    (): () = {
    self.value = 0
  }
}

let main: (): i32 = {
  let value = choice.Some(wrapper { resource: resource { value: 42 } })
  42
}

test("drop_glue.sc") {
  std.test.assert(main() == 42)
}
