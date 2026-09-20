let Future = core.async.Future
let Poll = core.async.Poll
let Result = core.Result
let throwing = core.error.throwing

let resource = struct {
  drops: Ptr<mut><i32>,
}

extend(resource, Droppable) {
  let drop = { (self: Borrow<mut><self>)(): () =>
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

  let poll = { <r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> =>
    if(self.polls == 0) {
      self.polls = 1
      Poll<i32>.Pending
    } else: {
      Poll<i32>.Ready(self.value)
    }
  }
}

let choose = { with<throwing<bool>>(fail: bool): i32 =>
  if(fail) {
    throw(true)
  } else: {
    40
  }
}

let make_step = { with<throwing<bool>>(move resource: resource, fail: bool): step =>
  step { polls: 0, value: choose(fail), resource: resource }
}

let run_success = { (drops: Ptr<mut><i32>): i32 =>
  let result: Result<bool><i32> = try {
    let resource = resource { drops: drops }
    let mut future = async {
      await(make_step(resource, false))
    }
    let first = future.poll()
    let second = future.poll()
    match(first) {
      Pending => do {
        match(second) { Ready(value) => value, Pending => 0,
        }
      }, Ready(_) => 0,
    }
  }

  match(result) {
    Ok(value) => do {
      if(value == 40) { 42 } else: { 0 }
    }, Err(_) => 0,
  }
}

let run_throwing = { (drops: Ptr<mut><i32>): i32 =>
  let result: Result<bool><i32> = try {
    let resource = resource { drops: drops }
    let mut future = async {
      await(make_step(resource, true))
    }
    let first = future.poll()
    let second = future.poll()
    match(first) {
      Pending => do {
        match(second) { Ready(value) => value, Pending => 0,
        }
      }, Ready(_) => 0,
    }
  }

  match(result) {
    Ok(_) => 0, Err(error) => do {
      if(error) { 42 } else: { 0 }
    },
  }
}

let main = { (): i32 =>
  let drops = unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
  unsafe {
    *drops = 0
  }

  let success = run_success(drops)
  let failure = run_throwing(drops)
  let drop_count = unsafe {
    *drops
  }
  unsafe {
    raw_dealloc(drops, size_of<i32>, align_of<i32>)
  }

  if(success == 42 && failure == 42 && drop_count == 2) { 42 } else: { 0 }
}

test("async_residual_failure_tail_await.sc") {
  std.test.assert(main() == 42)
}
