let Poll = core.async.Poll
let Future = core.async.Future
let unsafety = core.unsafe.unsafety

let first = struct {
  counter: Ptr<mut><i32>
  }

let second = struct {
  counter: Ptr<mut><i32>
  }

let marker = struct {
  counter: Ptr<mut><i32>,
  amount: i32
}

extend<marker, Droppable> {
  let drop(self: Borrow<mut><self>)
    (): () = {
    unsafe {
      *self.counter = *self.counter + self.amount
    }
  }
}

extend<first, Droppable> {
  let drop(self: Borrow<mut><self>)
    (): () = {
    unsafe {
      *self.counter = *self.counter + 10
    }
  }
}

extend<second, Droppable> {
  let drop(self: Borrow<mut><self>)
    (): () = {
    unsafe {
      *self.counter = *self.counter + 1
    }
  }
}

extend<first, Future<()>> {
  let Output = i32;

  let poll<r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> = {
    Poll<i32>.Pending
  }
}

extend<second, Future<()>> {
  let Output = i32;

  let poll<r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> = {
    Poll<i32>.Pending
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

let main(): i32 = {
  unsafe {
    let counter = allocate()
    *counter = 0
    do {
      let mut future = async {
        if(false) {
          let marker = marker { counter: counter, amount: 1000 }
          await(first { counter: counter })
        } else: {
          let marker = marker { counter: counter, amount: 100 }
          await(second { counter: counter })
        }
      }
      match(future.poll()) {
        Pending => (),
        Ready(_) => (),
      }
    }
    let drops = *counter
    release(counter)
    drops - 59
  }
}

test<"async_await_control_cancel.sc"> {
  std.test.assert(main() == 42)
}
