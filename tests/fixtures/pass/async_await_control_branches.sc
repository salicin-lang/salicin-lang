let Poll = core.async.Poll
let Future = core.async.Future

let step = struct {
  polled: bool,
  value: i32
}

extend(step, Future<()>) {
  let Output = i32;

  let poll = { <r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> =>
    if(self.polled) {
      Poll<i32>.Ready(self.value)
    } else: {
      self.polled = true
      Poll<i32>.Pending
    }
  }
}

let other_step = struct {
  polled: bool,
  value: i32
}

extend(other_step, Future<()>) {
  let Output = i32;

  let poll = { <r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> =>
    if(self.polled) {
      Poll<i32>.Ready(self.value)
    } else: {
      self.polled = true
      Poll<i32>.Pending
    }
  }
}

let step = {
  (value: i32): step =>
  step { polled: false, value: value }
}

let other_step = {
  (value: i32): other_step =>
  other_step { polled: false, value: value }
}

let choice = enum {
  left,
  right
}

let main = {
  (): i32 =>
  let mut conditional = async {
    let value = if(true) {
      let prefix = 19
      let child = await(step(1))
      prefix + child
    } else: {
      0
    }
    value
  }
  match(conditional.poll()) { Pending => (), Ready(_) => (),
  }
  let first = match(conditional.poll()) { Ready(value) => value, Pending => 0,
  }

  let mut matched = async {
    let value = match(choice.left) { choice.left => await(step(22)), choice.right => await(other_step(0)),
    }
    value
  }
  match(matched.poll()) { Pending => (), Ready(_) => (),
  }
  let second = match(matched.poll()) { Ready(value) => value, Pending => 0,
  }

  first + second
}

test("async_await_control_branches.sc") {
  std.test.assert(main() == 42)
}
