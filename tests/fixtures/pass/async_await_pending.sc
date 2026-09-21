let Poll = core.async.Poll
let Future = core.async.Future

let step = struct { polls: i32 }

extend(step, Future<()>) {
  let Output = i32;

  let poll: <r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> = {
    if(self.polls == 0) {
      self.polls = 1
      Poll<i32>.Pending
    } else: {
      Poll<i32>.Ready(41)
    }
  }
}

let main: (): i32 = {
  let offset = 1
  let mut future = async {
    let value = await(step { polls: 0 })
    value + offset
  }
  let first = match(future.poll()) {
    Pending => 1,
    Ready(_) => 0,
  }
  let second = match(future.poll()) {
    Pending => 0,
    Ready(value) => value,
  }
  first + second - 1
}

test("async_await_pending.sc") {
  std.test.assert(main() == 42)
}
