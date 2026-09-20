let Poll = core.async.Poll
let Future = core.async.Future

let step = struct {
  remaining: Ptr<mut><i32>
  }

extend(step, Future<()>) {
  let Output = i32;

  let poll = { <r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> =>
    let value = unsafe {
      *self.remaining = *self.remaining - 1
      *self.remaining
    }
    Poll<i32>.Ready(value)
  }
}

let step = { (remaining: Ptr<mut><i32>): step =>
  step { remaining: remaining }
}

let main = { (): i32 =>
  let mut remaining = 3
  let remaining_ptr = ptr<mut>(borrow<mut>(remaining))
  let mut future = async {
    loop {
      let value = await(step(remaining_ptr))
      if(value == 0) {
        break(value + 42)
      } else: {
        continue()
      }
    }
  }

  match(future.poll()) { Pending => 0, Ready(value) => value,
  }
}

test("async_await_loop_value.sc") {
  std.test.assert(main() == 42)
}
