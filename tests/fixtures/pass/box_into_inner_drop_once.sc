let Box = alloc.Box

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
    let boxed = Box.new<T: resource>(resource{ counter: counter })
    let resource = boxed.into_inner()
  }
  let drops = unsafe {
    *counter
  }
  unsafe {
    raw_dealloc(counter, size_of<i32>, align_of<i32>)
  }
  41 + drops
}

test("box_into_inner_drop_once.sc") {
  std.test.assert(main() == 42)
}
