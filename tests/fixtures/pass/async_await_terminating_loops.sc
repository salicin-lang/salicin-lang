let Poll = core.async.Poll
let Future = core.async.Future

let step = struct {
  polled: bool,
  value: i32
}

extend(step, Future<()>) {
  let Output = i32;

  let poll<r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> = {
    if self.polled {
      Poll<i32>.Ready(self.value)
    } else {
      self.polled = true
      Poll<i32>.Pending
    }
  }
}

let step(value: i32): step = {
  step{ polled: false, value: value }
}

let condition = struct {
  polled: bool,
  value: bool
}

extend(condition, Future<()>) {
  let Output = bool;

  let poll<r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<bool> = {
    if self.polled {
      Poll<bool>.Ready(self.value)
    } else {
      self.polled = true
      Poll<bool>.Pending
    }
  }
}

let condition(value: bool): condition = {
  condition{ polled: false, value: value }
}

let main(): i32 = {
  let mut value_loop = async {
    loop {
      break(await step(40))
    }
  }
  let loop_pending = match(value_loop.poll()) { Pending => 1, Ready(_) => 0,
  }
  let loop_value = match(value_loop.poll()) { Pending => 0, Ready(value) => value,
  }

  let mut true_while = async {
    while { true } {
      let ignored = await step(0);
      break()
    }
  }
  let while_pending = match(true_while.poll()) { Pending => 1, Ready(_) => 0,
  }
  let while_ready = match(true_while.poll()) { Pending => 0, Ready(_) => 1,
  }

  let mut false_while = async {
    while { false } {
      let ignored = await step(0);
      break()
    }
  }
  let false_ready = match(false_while.poll()) { Pending => 0, Ready(_) => 1,
  }

  let mut awaited_condition = async {
    while { await condition(false) } {
      break()
    }
  }
  let condition_pending = match(awaited_condition.poll()) { Pending => 1, Ready(_) => 0,
  }
  let condition_ready = match(awaited_condition.poll()) { Pending => 0, Ready(_) => 1,
  }

  loop_value + loop_pending + while_pending + while_ready + false_ready + condition_pending +
    condition_ready - 4
}

test("async_await_terminating_loops.sc") {
  std.test.assert(main() == 42)
}
