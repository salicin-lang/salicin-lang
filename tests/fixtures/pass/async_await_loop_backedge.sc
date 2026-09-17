let Poll = core.async.Poll
let Future = core.async.Future

let step = struct {
  polled: bool,
  remaining: Ptr<mut><i32>
}

extend(step, Future<()>) {
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

let next_step(remaining: Ptr<mut><i32>): step = {
  step{ polled: false, remaining: remaining }
}

let ready_step = struct {
  remaining: Ptr<mut><i32>
}

extend(ready_step, Future<()>) {
  let Output = bool;

  let poll<r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<bool> = {
    let done = unsafe {
      *self.remaining = *self.remaining - 1
      *self.remaining == 0
    }
    Poll<bool>.Ready(done)
  }
}

let ready_step(remaining: Ptr<mut><i32>): ready_step = {
  ready_step{ remaining: remaining }
}

let main(): i32 = {
  let mut remaining = 3
  let remaining_ptr = ptr<mut>(borrow<mut>(remaining))
  let mut future = async {
    loop {
      let done = await next_step(remaining_ptr)
      if done {
        break()
      } else {
        continue()
      }
    }
  }

  let first = match future.poll()
    { Pending -> 1 }
    { Ready(_) -> 0 }
  let second = match future.poll()
    { Pending -> 1 }
    { Ready(_) -> 0 }
  let third = match future.poll()
    { Pending -> 1 }
    { Ready(_) -> 0 }
  let fourth = match future.poll()
    { Pending -> 0 }
    { Ready(_) -> 39 }

  let mut immediate_remaining = 3
  let immediate_ptr = ptr<mut>(borrow<mut>(immediate_remaining))
  let mut immediate = async {
    loop {
      let done = await ready_step(immediate_ptr)
      if done {
        break()
      } else {
        continue()
      }
    }
  }
  let immediate_ready = match immediate.poll()
    { Pending -> 0 }
    { Ready(_) -> 1 }

  first + second + third + fourth + immediate_ready - 1
}

test("async_await_loop_backedge.sc") {
  std.test.assert(main() == 42)
}
