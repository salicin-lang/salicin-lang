let Box = alloc.Box

let resource = struct { counter: Ptr<mut><i32> }

extend(resource, Droppable) {
  let drop = { (self: Borrow<mut><self>)(): () =>
    unsafe {
      *self.counter = *self.counter + 1
    }
  }
}

let main = { (): i32 =>
  let mut count = 0
  do {
    let counter = ptr<mut>(borrow<mut>(count))
    let boxed = Box.new(resource { counter: counter })
    let pointer = boxed.into_raw()
    let rebuilt = unsafe {
      Box<resource>.from_raw(pointer)
    }
  }
  41 + count
}

test("box_raw_roundtrip_drop_once.sc") {
  std.test.assert(main() == 42)
}
