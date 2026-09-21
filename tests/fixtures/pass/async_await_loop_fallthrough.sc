let Poll = core.async.Poll
let Future = core.async.Future

let step = struct {
  remaining: Ptr<mut><i32>
  }

extend(step, Future<()>) {
  let Output = bool;

  let poll: <r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<bool> = {
    let done = unsafe {
      *self.remaining = *self.remaining - 1
      *self.remaining == 0
    }
    Poll<bool>.Ready(done)
  }
}

let step: (remaining: Ptr<mut><i32>): step = {
  step { remaining: remaining }
}

let main: (): i32 = {
  let mut implicit_remaining = 3
  let implicit_ptr = ptr<mut>(borrow<mut>(implicit_remaining))
  let mut implicit = async {
    loop {
      let done = await(step(implicit_ptr))
      if(done) {
        break(21)
      }
    }
  }
  let implicit_value = match(implicit.poll()) {
    Pending => 0,
    Ready(value) => value,
  }

  let mut explicit_remaining = 3
  let mut fallthroughs = 0
  let explicit_ptr = ptr<mut>(borrow<mut>(explicit_remaining))
  let fallthroughs_ptr = ptr<mut>(borrow<mut>(fallthroughs))
  let mut explicit = async {
    loop {
      let done = await(step(explicit_ptr))
      if(done) {
        break(21)
      } else: {
        unsafe {
          *fallthroughs_ptr = *fallthroughs_ptr + 1
        }
      }
    }
  }
  let explicit_value = match(explicit.poll()) {
    Pending => 0,
    Ready(value) => value,
  }

  implicit_value + explicit_value + unsafe { *fallthroughs_ptr } - 2
}

test("async_await_loop_fallthrough.sc") {
  std.test.assert(main() == 42)
}
