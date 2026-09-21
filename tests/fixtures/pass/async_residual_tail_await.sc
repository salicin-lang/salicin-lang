let Future = core.async.Future
let Poll = core.async.Poll

let ask = effect {
  ask: (): i32
}

let resource = struct {
  drops: Ptr<mut><i32>,
}

extend(resource, Droppable) {
  let drop: (self: Borrow<mut><self>)
    (): () = {
    unsafe {
      *self.drops = *self.drops + 1
    }
  }
}

let step = struct {
  polls: i32,
  value: i32,
  resource: resource,
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

let request: with<ask>
  (): i32 = {
  ask.ask()
}

let make_step: with<ask>
  (drops: Ptr<mut><i32>): step = {
  step { polls: 0, value: request(), resource: resource { drops: drops } }
}

let run: (drops: Ptr<mut><i32>): i32 = {
  ask.handle {
    ask: { (resume) => resume(40) },
    action: {
      let mut future = async {
        await(make_step(drops))
      }
      let first = future.poll()
      let second = future.poll()
      match(first) {
        Pending => do {
          match(second) {
            Ready(value) => value,
            Pending => 0,
          }
        },
        Ready(_) => 0,
      }
    },
  }
}

let cancel: (drops: Ptr<mut><i32>): () = {
  ask.handle {
    ask: { (resume) => resume(2) },
    action: {
      let mut cancelled = async {
        await(make_step(drops))
      }
      let pending = cancelled.poll()
      match(pending) {
        Pending => (),
        Ready(_) => (),
      }
    },
  }
}

let main: (): i32 = {
  let drops = unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
  unsafe {
    *drops = 0
  }

  let value = run(drops)
  cancel(drops)
  let drop_count = unsafe {
    *drops
  }
  unsafe {
    raw_dealloc(drops, size_of<i32>, align_of<i32>)
  }
  value + drop_count
}

test("async_residual_tail_await.sc") {
  std.test.assert(main() == 42)
}
