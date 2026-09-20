let resource = struct { value: i32 }

extend(resource, Droppable) {
  let drop = { (self: Borrow<mut><self>)(): () =>
    let trap = 1 / self.value
  }
}

let main = { (): i32 =>
  let resource = resource { value: 0 }
  forget(resource)
  42
}

test("forget_resource.sc") {
  std.test.assert(main() == 42)
}
