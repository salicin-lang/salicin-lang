let Future = core.async.Future
let Poll = core.async.Poll
let Result = core.Result
let throwing = core.error.throwing

let step = struct {
  polls: i32,
  value: i32,
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

let make_step: with<throwing<bool>>(fail: bool): step = {
  if fail {
    throw(true)
  } else {
    step{ polls: 0, value: 40 }
  }
}

let run(fail: bool): i32 = {
  let result: Result<bool><i32> = try {
    let mut future = async {
      let value = await make_step(fail)
      value + 2
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
    Ok(value) => value, Err(error) => do {
      if error { 42 } else { 0 }
    },
  }
}

let main(): i32 = {
  let success = run(false)
  let failure = run(true)
  if success == 42 && failure == 42 {
    42
  } else {
    0
  }
}

test("async_residual_failure_await.sc") {
  std.test.assert(main() == 42)
}
