let Poll = core.async.Poll
let Future = core.async.Future
let unsafety = core.unsafe.unsafety

let step = struct {
  counter: Ptr<mut><i32>,
  polls: i32,
  value: i32
}
let resource = struct { counter: Ptr<mut><i32> }

extend(step, Droppable) {
  let drop: (self: Borrow<mut><self>)
    (): () = {
    unsafe {
      *self.counter = *self.counter + 1
    }
  }
}

extend(resource, Droppable) {
  let drop: (self: Borrow<mut><self>)
    (): () = {
    unsafe {
      *self.counter = *self.counter + 1
    }
  }
}

extend(step, Future<()>) {
  let Output = i32;

  let poll: <r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> = {
    if(self.polls == 0) {
      self.polls = 1
      Poll<i32>.Pending
    } else: {
      Poll<i32>.Ready(self.value)
    }
  }
}

let consume: (move resource: resource): () = { () }

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

    do {
      let resource = resource { counter: counter }
      let mut future = async {
        let first = await(step { counter: counter, polls: 0, value: 20 })
        let second = await(step { counter: counter, polls: 0, value: 22 })
        consume(resource)
        first + second
      }
      match(future.poll()) {
        Pending => (),
        Ready(_) => (),
      }
      match(future.poll()) {
        Pending => (),
        Ready(_) => (),
      }
    }

    let drops = *counter
    release(counter)
    39 + drops
  }
}

test("async_await_multiple_cancel.sc") {
  std.test.assert(main() == 42)
}
