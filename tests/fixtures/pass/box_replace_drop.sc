let Box = alloc.Box

let resource = struct { counter: Ptr<mut><i32>, value: i32 }

extend(resource, Droppable) {
  let drop: (self: Borrow<mut><self>)
    (): () = {
    unsafe {
      *self.counter = *self.counter + 1
    }
  }
}

let main: (): i32 = {
  let counter = unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
  unsafe {
    *counter = 0
  }
  do {
    let mut boxed = Box.new<T: resource>(resource { counter: counter, value: 10 })
    do {
      let previous = boxed.replace(resource { counter: counter, value: 20 })
    }
  }
  let drops = unsafe {
    *counter
  }
  unsafe {
    raw_dealloc(counter, size_of<i32>, align_of<i32>)
  }
  40 + drops
}

test("box_replace_drop.sc") {
  std.test.assert(main() == 42)
}
