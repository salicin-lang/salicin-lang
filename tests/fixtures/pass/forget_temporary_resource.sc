let resource = struct { value: i32 }

extend(resource, Droppable) {
  let drop(self: Borrow<mut><self>)(): () = {
    let trap = 1 / self.value
  }
}

let main(): i32 = {
  forget(resource{ value: 0 })
  42
}

test("forget_temporary_resource.sc") {
  std.test.assert(main() == 42)
}
