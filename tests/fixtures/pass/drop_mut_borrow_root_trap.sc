let resource = struct { value: i32 }

extend(resource, Droppable) {
  let drop(self: Borrow<mut><self>)(): () = {
    let trapped = 1 / self.value
  }
}

let replace(target: Borrow<mut><resource>)(move replacement: resource): () = {
  target = replacement
}

let main(): i32 = {
  let mut resource = resource{ value: 0 }
  replace(resource)(resource{ value: 1 })
  0
}

test("drop_mut_borrow_root_trap.sc") {
  std.test.assert(main() == 42)
}
