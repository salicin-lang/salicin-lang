let Future = core.async.Future
let Poll = core.async.Poll

let ask = effect {
  ask: (): i32
}

let step = struct {
  polls: i32,
  value: i32,
}

extend(step, Future<()>) {
  let Output = i32;

  let poll: <r: region> = { (self: Borrow<mut><r><self>)
    (): Poll<i32> =>
    if(self.polls == 0) {
      self.polls = 1
      Poll<i32>.Pending
    } else: {
      Poll<i32>.Ready(self.value)
    }
  }
}

let make_step = { with<ask>
  (): step =>
  step { polls: 0, value: ask.ask() }
}

let make_step_with = { with<ask>
  (offset: Borrow<i32>): step =>
  step { polls: 0, value: ask.ask() + offset }
}

let shared = {
  (offset: Borrow<i32>): i32 =>
  let mut future = async {
    let value = await(make_step_with(offset))
    value
  }
  ask.handle {
    ask: { (resume) => resume(40) },
    action: { let first = future.poll()
      let second = future.poll()
      match(first) {
        Pending => do {
          match(second) { Ready(value) => value, Pending => 0,
          }
        }, Ready(_) => 0,
      }
    },
  }
}

let mutable = {
  (value: Borrow<mut><i32>): i32 =>
  let mut future = async {
    let amount = await(make_step())
    value = value + amount
    value
  }
  ask.handle {
    ask: { (resume) => resume(40) },
    action: { let first = future.poll()
      let second = future.poll()
      match(first) {
        Pending => do {
          match(second) { Ready(result) => result, Pending => 0,
          }
        }, Ready(_) => 0,
      }
    },
  }
}

let cancelled = {
  (value: Borrow<mut><i32>): i32 =>
  do {
    let mut future = async {
      let amount = await(make_step())
      value = value + amount
      value
    }
    let handled: () = ask.handle {
      ask: { (resume) => resume(40) },
      action: {
        match(future.poll()) { Pending => (), Ready(_) => (),
        }
      },
    }
    handled
  }
  value = 42
  value
}

let main = {
  (): i32 =>
  let offset = 2
  let mut first = 2
  let mut second = 2
  let shared_result = shared(offset)
  let mutable_result = mutable(first)
  let cancelled_result = cancelled(second)
  if(shared_result == 42 && mutable_result == 42 && cancelled_result == 42) {
    42
  } else: {
    0
  }
}

test("async_residual_borrow_await.sc") {
  std.test.assert(main() == 42)
}
