let resource = struct { value: i32 }

extend(resource, Droppable) {
  let drop = (self: Borrow<mut><self>)(): () => {
    let checked = 1 / self.value
    self.value = 0
  }
}

let inspect = (resource: Borrow<resource>): i32 => { resource.value }

let main = (): i32 => { inspect(resource { value: 42 }) }

test("temporary_borrow_argument_drop.sc") {
  std.test.assert(main() == 42)
}
