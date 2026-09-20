let Poll = core.async.Poll
let Future = core.async.Future

let step = struct {
  polls: i32,
  value: i32
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

let main = { (): i32 =>
  let mut future = async {
    let first = await(step { polls: 0, value: 10 })
    let second = await(step { polls: 0, value: 12 })
    let third = await(step { polls: 0, value: 20 })
    first + second + third
  }

  let first_poll = match(future.poll()) { Pending => 1, Ready(_) => 0,
  }
  let second_poll = match(future.poll()) { Pending => 1, Ready(_) => 0,
  }
  let third_poll = match(future.poll()) { Pending => 1, Ready(_) => 0,
  }
  let fourth_poll = match(future.poll()) { Pending => 0, Ready(value) => value,
  }
  first_poll + second_poll + third_poll + fourth_poll - 3
}

test("async_await_multiple.sc") {
  std.test.assert(main() == 42)
}
