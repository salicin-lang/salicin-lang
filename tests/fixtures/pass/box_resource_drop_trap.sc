let Box = alloc.Box

let resource = struct { value: i32 }

extend(resource, Droppable) {
  let drop = {
    (self: Borrow<mut><self>)
    (): () =>
    let trapped = 1 / self.value
  }
}

let main = {
  (): i32 =>
  let boxed = Box.new(resource { value: 0 })
  0
}

test("box_resource_drop_trap.sc") {
  std.test.assert(main() == 42)
}
