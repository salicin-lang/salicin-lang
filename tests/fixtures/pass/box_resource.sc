let Box = alloc.Box

let resource = struct { value: i32 }

extend<resource, Droppable> {
  let drop(self: Borrow<mut><self>)
    (): () = {
    let checked = 1 / self.value
  }
}

let main(): i32 = {
  let boxed = Box.new(resource { value: 1 })
  42
}

test<"box_resource.sc"> {
  std.test.assert(main() == 42)
}
