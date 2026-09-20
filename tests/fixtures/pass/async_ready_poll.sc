let Poll = core.async.Poll
let Future = core.async.Future
let unsafety = core.unsafe.unsafety

let resource = struct { counter: Ptr<mut><i32> }

extend(resource, Droppable) {
  let drop = (self: Borrow<mut><self>)(): () => {
    unsafe {
      *self.counter = *self.counter + 1
    }
  }
}

let consume = (move resource: resource): () => { () }

let allocate = with<unsafety>(): Ptr<mut><i32> => {
  unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
}

let release = with<unsafety>(counter: Ptr<mut><i32>): () => {
  unsafe {
    raw_dealloc(counter, size_of<i32>, align_of<i32>)
  }
}

let main = (): i32 => {
  unsafe {
    let counter = allocate()
    *counter = 0

    let resource = resource { counter: counter }
    let mut future = async {
      consume(resource)
    }
    let result = future.poll()
    let ready = match(result) { Ready(_) => 1, Pending => 0,
    }

    let drops = *counter
    release(counter)
    40 + ready + drops
  }
}

test("async_ready_poll.sc") {
  std.test.assert(main() == 42)
}
