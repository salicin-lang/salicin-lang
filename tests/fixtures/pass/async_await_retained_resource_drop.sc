let Poll = core.async.Poll
let Future = core.async.Future
let unsafety = core.unsafe.unsafety

let resource = struct {
  counter: Ptr<mut><i32>,
  value: i32
}

extend(resource, Droppable) {
  let drop = { (self: Borrow<mut><self>)(): () =>
    unsafe {
      *self.counter = *self.counter + 1
    }
  }
}

let step = struct {
  counter: Ptr<mut><i32>,
  polled: bool
}

extend(step, Droppable) {
  let drop = { (self: Borrow<mut><self>)(): () =>
    unsafe {
      *self.counter = *self.counter + 1
    }
  }
}

extend(step, Future<()>) {
  let Output = i32;

  let poll = { <r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> =>
    if(self.polled) {
      Poll<i32>.Ready(0)
    } else: {
      self.polled = true
      Poll<i32>.Pending
    }
  }
}

let allocate = { with<unsafety>(): Ptr<mut><i32> =>
  unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
}

let release = { with<unsafety>(counter: Ptr<mut><i32>): () =>
  unsafe {
    raw_dealloc(counter, size_of<i32>, align_of<i32>)
  }
}

let main = { (): i32 =>
  unsafe {
    let counter = allocate()
    *counter = 0
    let mut future = async {
      let resource = resource { counter: counter, value: 40 }
      let awaited = await(step { counter: counter, polled: false })
      resource.value + awaited
    }
    let pending = match(future.poll()) { Pending => 0, Ready(_) => 100,
    }
    let result = match(future.poll()) { Ready(value) => value, Pending => 100,
    }

    do {
      let mut cancelled = async {
        let resource = resource { counter: counter, value: 0 }
        let awaited = await(step { counter: counter, polled: false })
        resource.value + awaited
      }
      match(cancelled.poll()) { Pending => (), Ready(_) => (),
      }
    }

    let drops = *counter
    release(counter)
    pending + result + drops - 2
  }
}

test("async_await_retained_resource_drop.sc") {
  std.test.assert(main() == 42)
}
