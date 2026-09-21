let Poll = core.async.Poll
let Future = core.async.Future

let step = struct {
  remaining: Ptr<mut><i32>
  }

extend(step, Future<()>) {
  let Output = bool;

  let poll: <r: region> = { (self: Borrow<mut><r><self>)
    (): Poll<bool> =>
    let done = unsafe {
      *self.remaining = *self.remaining - 1
      *self.remaining == 0
    }
    Poll<bool>.Ready(done)
  }
}

let step = {
  (remaining: Ptr<mut><i32>): step =>
  step { remaining: remaining }
}

let main = {
  (): i32 =>
  let mut remaining = 4
  let remaining_ptr = ptr<mut>(borrow<mut>(remaining))
  let mut future = async {
    loop {
      if(unsafe { *remaining_ptr % 2 == 0 }) {
        let done = await(step(remaining_ptr))
        if(done) {
          break()
        } else: {
          continue()
        }
      } else: {
        let done = await(step(remaining_ptr))
        if(done) {
          break()
        } else: {
          ()
        }
      }
    }
  }

  match(future.poll()) { Pending => 0, Ready(_) => 42,
  }
}

test("async_await_loop_nested_control.sc") {
  std.test.assert(main() == 42)
}
