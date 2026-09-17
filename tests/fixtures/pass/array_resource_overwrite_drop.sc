let resource = struct { counter: Ptr<mut><i32> }

extend(resource, Droppable) {
  let drop(self: Borrow<mut><self>)(): () = {
    unsafe {
      *self.counter = *self.counter + 1
    }
  }
}

let main(): i32 = {
  let counter = unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
  unsafe {
    *counter = 0
  }
  do {
    let mut values = [resource{ counter: counter }, resource{ counter: counter }]
    values[0] = resource{ counter: counter }
  }
  let drops = unsafe {
    *counter
  }
  unsafe {
    raw_dealloc(counter, size_of<i32>, align_of<i32>)
  }
  39 + drops
}

test("array_resource_overwrite_drop.sc") {
  std.test.assert(main() == 42)
}
