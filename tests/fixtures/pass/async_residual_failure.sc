let Future = core.async.Future
let Poll = core.async.Poll
let Result = core.Result
let throwing = core.error.throwing

let resource = struct {
  value: i32,
  drops: Ptr<mut><i32>,
}

extend<resource, Droppable> {
  let drop(self: Borrow<mut><self>)
    (): () = {
    unsafe {
      *self.drops = *self.drops + 1
    }
  }
}

let choose: with<throwing<bool>>(fail: bool, value: i32): i32 = {
  if(fail) {
    throw(true)
  } else: {
    value
  }
}

let consume_or_throw: with<throwing<bool>>(move resource: resource): i32 = {
  choose(true, resource.value)
}

let poll_once<e: effects, f: type, t: type>: with<e>
  (future: Borrow<mut><f>): Poll<t> requires<f is Future<e> && f.Output == t> = {
  future.poll()
}

let main(): i32 = {
  let offset = 42
  let mut success = async {
    choose(false, offset)
  }
  let success_result: Result<bool><Poll<i32>> = try {
    success.poll()
  }
  let success_value = match(success_result) {
    Ok(polled) => do {
      match(polled) {
        Ready(value) => value,
        Pending => 0,
      }
    },
    Err(_) => 0,
  }

  let drops = unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
  unsafe {
    *drops = 0
  }
  let resource = resource { value: 2, drops: drops }
  let mut failure = async {
    consume_or_throw(resource)
  }
  let failure_result: Result<bool><Poll<i32>> = try {
    failure.poll()
  }
  let failed = match(failure_result) {
    Ok(_) => false,
    Err(error) => error,
  }
  let drop_count = unsafe {
    *drops
  }
  unsafe {
    raw_dealloc(drops, size_of<i32>, align_of<i32>)
  }

  if(success_value == 42 && failed && drop_count == 1) {
    42
  } else: {
    0
  }
}

test<"async_residual_failure.sc"> {
  std.test.assert(main() == 42)
}
