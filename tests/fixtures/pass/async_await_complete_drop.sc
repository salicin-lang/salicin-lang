let Poll = core.async.Poll
let Future = core.async.Future
let unsafety = core.unsafe.unsafety

let step = struct { counter: Ptr<mut><i32> }
let resource = struct { counter: Ptr<mut><i32> }

extend<step, Droppable> {
  let drop: (self: Borrow<mut><self>)
    (): () = {
    unsafe {
      *self.counter = *self.counter + 1
    }
  }
}

extend<resource, Droppable> {
  let drop: (self: Borrow<mut><self>)
    (): () = {
    unsafe {
      *self.counter = *self.counter + 1
    }
  }
}

let consume: (move resource: resource): () = { () }

extend<step, Future<()>> {
  let Output = i32;

  let poll: <r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> = {
    Poll<i32>.Ready(40)
  }
}

let allocate: with<unsafety>
  (): Ptr<mut><i32> = {
  unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
}

let release: with<unsafety>
  (counter: Ptr<mut><i32>): () = {
  unsafe {
    raw_dealloc(counter, size_of<i32>, align_of<i32>)
  }
}

let main: (): i32 = {
  unsafe {
    let counter = allocate()
    *counter = 0

    let value = do {
      let resource = resource { counter: counter }
      let mut future = async {
        let value = await(step { counter: counter })
        consume(resource)
        value
      }
      match(future.poll()) {
        Pending => 0,
        Ready(value) => value,
      }
    }

    let drops = *counter
    release(counter)
    value + drops
  }
}

test<"async_await_complete_drop.sc"> {
  std.test.assert(main() == 42)
}
