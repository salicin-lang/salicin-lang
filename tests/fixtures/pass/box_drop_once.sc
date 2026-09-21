let Box = alloc.Box

let resource = struct { counter: Ptr<mut><i32> }

extend<resource, Droppable> {
  let drop: (self: Borrow<mut><self>)
    (): () = {
    unsafe {
      *self.counter = *self.counter + 1
    }
  }
}

let main: (): i32 = {
  let mut count = 0
  do {
    let counter = ptr<mut>(borrow<mut>(count))
    let first = Box.new<T: resource>(resource { counter: counter })
    let second = first
  }
  41 + count
}

test<"box_drop_once.sc"> {
  std.test.assert(main() == 42)
}
