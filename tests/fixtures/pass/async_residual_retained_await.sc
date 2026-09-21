let Future = core.async.Future
let Poll = core.async.Poll
let Result = core.Result
let throwing = core.error.throwing

let resource = struct {
  drops: Ptr<mut><i32>,
  value: i32,
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

let make_step: with<throwing<bool>>(fail: bool): step = {
  if(fail) {
    throw(true)
  } else: {
    step { polls: 0, value: 2 }
  }
}

let run_success: (drops: Ptr<mut><i32>): i32 = {
  let result: Result<bool><i32> = try {
    let outer = resource { drops: drops, value: 1 }
    let mut future = async {
      let inner = resource { drops: drops, value: 39 }
      let value = await(make_step(false))
      outer.value + inner.value + value
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
  }
  match(result) {
    Ok(value) => value,
    Err(_) => 0,
  }
}

let run_throwing: (drops: Ptr<mut><i32>): i32 = {
  let result: Result<bool><i32> = try {
    let outer = resource { drops: drops, value: 1 }
    let mut future = async {
      let inner = resource { drops: drops, value: 39 }
      let value = await(make_step(true))
      outer.value + inner.value + value
    }
    match(future.poll()) {
      Pending => 0,
      Ready(value) => value,
    }
  }
  match(result) {
    Ok(_) => 0,
    Err(error) => do {
      if(error) { 42 } else: { 0 }
    },
  }
}

let run_cancelled: (drops: Ptr<mut><i32>): i32 = {
  let result: Result<bool><i32> = try {
    let outer = resource { drops: drops, value: 1 }
    let mut future = async {
      let inner = resource { drops: drops, value: 39 }
      let value = await(make_step(false))
      outer.value + inner.value + value
    }
    match(future.poll()) {
      Pending => 42,
      Ready(_) => 0,
    }
  }
  match(result) {
    Ok(value) => value,
    Err(_) => 0,
  }
}

let main: (): i32 = {
  let drops = unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
  unsafe {
    *drops = 0
  }

  let success = run_success(drops)
  let failure = run_throwing(drops)
  let cancelled = run_cancelled(drops)
  let drop_count = unsafe {
    *drops
  }
  unsafe {
    raw_dealloc(drops, size_of<i32>, align_of<i32>)
  }

  if(success == 42 && failure == 42 && cancelled == 42 && drop_count == 6) {
    42
  } else: {
    0
  }
}

test("async_residual_retained_await.sc") {
  std.test.assert(main() == 42)
}
