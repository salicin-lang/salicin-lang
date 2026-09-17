let Poll = core.async.Poll
let Future = core.async.Future

let resource = struct {
  drops: Ptr<mut><i32>
}

extend(resource, Droppable) {
  let drop(self: Borrow<mut><self>)(): () = {
    unsafe {
      *self.drops = *self.drops + 1
    }
  }
}

let step = struct {
  polled: bool,
  remaining: Ptr<mut><i32>
}

extend(step, Future(())) {
  let Output = bool;

  let poll<r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<bool> = {
    if self.polled {
      let done = unsafe {
        *self.remaining = *self.remaining - 1
        *self.remaining == 0
      }
      Poll<bool>.Ready(done)
    } else {
      self.polled = true
      Poll<bool>.Pending
    }
  }
}

let step(remaining: Ptr<mut><i32>): step = {
  step{ polled: false, remaining: remaining }
}

let consume(move first: resource, move second: resource): i32 = {
  38
}

let main(): i32 = {
  let mut drops = 0
  let drops_ptr = ptr<mut>(borrow<mut>(drops))
  let output = do {
    let first_resource = resource{ drops: drops_ptr }
    let second_resource = resource{ drops: drops_ptr }
    let mut remaining = 2
    let remaining_ptr = ptr<mut>(borrow<mut>(remaining))
    let mut future = async {
      loop {
        let done = await step(remaining_ptr)
        if done {
          break(consume(first_resource, second_resource))
        } else {
          continue()
        }
      }
    }
    match future.poll()
      { Pending -> () }
      { Ready(_) -> () }
    match future.poll()
      { Pending -> () }
      { Ready(_) -> () }
    match future.poll()
      { Pending -> 0 }
      { Ready(value) -> value }
  }

  do {
    let first_resource = resource{ drops: drops_ptr }
    let second_resource = resource{ drops: drops_ptr }
    let mut remaining = 2
    let remaining_ptr = ptr<mut>(borrow<mut>(remaining))
    let mut cancelled = async {
      loop {
        let done = await step(remaining_ptr)
        if done {
          break(consume(first_resource, second_resource))
        } else {
          continue()
        }
      }
    }
    match cancelled.poll()
      { Pending -> () }
      { Ready(_) -> () }
  }

  output + unsafe { *drops_ptr }
}

test("async_await_loop_move_carry.sc") {
  std.test.assert(main() == 42)
}
