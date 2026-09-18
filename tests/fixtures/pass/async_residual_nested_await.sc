let Future = core.async.Future
let Poll = core.async.Poll

let ask = effect {
  let ask(): i32
}

let step = struct {
  drops: Ptr<mut><i32>,
  polls: i32,
  value: i32,
}

extend(step, Droppable) {
  let drop(self: Borrow<mut><self>)(): () = {
    unsafe {
      *self.drops = *self.drops + 1
    }
  }
}

extend(step, Future<()>) {
  let Output = i32;

  let poll<r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> = {
    if self.polls == 0 {
      self.polls = 1
      Poll<i32>.Pending
    } else {
      Poll<i32>.Ready(self.value)
    }
  }
}

let make_step: with<ask>(drops: Ptr<mut><i32>): step = {
  step{ drops: drops, polls: 0, value: ask.ask() }
}

let run_success(drops: Ptr<mut><i32>): i32 = {
  let mut future = async {
    let first = await make_step(drops)
    let second = await step{ drops: drops, polls: 0, value: first + 1 }
    second + 1
  }
  ask.handle{
    ask: { (resume) -> resume(40) },
    action: {
      let first = future.poll()
      let second = future.poll()
      let third = future.poll()
      match(first) {
        Pending => do {
          match(second) {
            Pending => do {
              match(third) { Ready(value) => value, Pending => 0,
              }
            }, Ready(_) => 0,
          }
        }, Ready(_) => 0,
      }
    },
  }
}

let run_cancelled(drops: Ptr<mut><i32>): i32 = {
  ask.handle{
    ask: { (resume) -> resume(40) },
    action: {
      let mut future = async {
        let first = await make_step(drops)
        let second = await step{ drops: drops, polls: 0, value: first + 1 }
        second + 1
      }
      let first = future.poll()
      let second = future.poll()
      match(first) {
        Pending => do {
          match(second) { Pending => 42, Ready(_) => 0,
          }
        }, Ready(_) => 0,
      }
    },
  }
}

let main(): i32 = {
  let drops = unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
  unsafe {
    *drops = 0
  }

  let success = run_success(drops)
  let cancelled = run_cancelled(drops)
  let drop_count = unsafe {
    *drops
  }
  unsafe {
    raw_dealloc(drops, size_of<i32>, align_of<i32>)
  }

  if success == 42 && cancelled == 42 && drop_count == 4 {
    42
  } else {
    0
  }
}

test("async_residual_nested_await.sc") {
  std.test.assert(main() == 42)
}
