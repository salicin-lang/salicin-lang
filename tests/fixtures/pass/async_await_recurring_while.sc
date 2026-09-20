let Poll = core.async.Poll
let Future = core.async.Future

let step = struct {
  remaining: Ptr<mut><i32>,
  polls: Ptr<mut><i32>
  }

extend(step, Future<()>) {
  let Output = ();

  let poll = { <r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<()> =>
    unsafe {
      *self.polls = *self.polls + 1
      *self.remaining = *self.remaining - 1
      Poll<()>.Ready(())
    }
  }
}

let step = { (remaining: Ptr<mut><i32>, polls: Ptr<mut><i32>): step =>
  step { remaining: remaining, polls: polls }
}

let pending_step = struct {
  polled: bool,
  remaining: Ptr<mut><i32>
  }

extend(pending_step, Future<()>) {
  let Output = ();

  let poll = { <r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<()> =>
    if(self.polled) {
      unsafe {
        *self.remaining = *self.remaining - 1
      }
      Poll<()>.Ready(())
    } else: {
      self.polled = true
      Poll<()>.Pending
    }
  }
}

let pending_step = { (remaining: Ptr<mut><i32>): pending_step =>
  pending_step { polled: false, remaining: remaining }
}

let main = { (): i32 =>
  let mut polls = 0
  let polls_ptr = ptr<mut>(borrow<mut>(polls))

  let mut pre_remaining = 3
  let pre_ptr = ptr<mut>(borrow<mut>(pre_remaining))
  let mut pre = async {
    while(unsafe { *pre_ptr > 0 }) {
      let ignored = await(step(pre_ptr, polls_ptr))
    }
  }
  let pre_ready = match(pre.poll()) { Pending => 0, Ready(_) => 1,
  }

  let mut false_remaining = 0
  let false_ptr = ptr<mut>(borrow<mut>(false_remaining))
  let mut initially_false = async {
    while(unsafe { *false_ptr > 0 }) {
      let ignored = await(step(false_ptr, polls_ptr))
    }
  }
  let false_ready = match(initially_false.poll()) { Pending => 0, Ready(_) => 1,
  }

  let mut post_remaining = 0
  let post_ptr = ptr<mut>(borrow<mut>(post_remaining))
  let mut post = async {
    do {
      let ignored = await(step(post_ptr, polls_ptr))
    }
    while: {
      unsafe { *post_ptr > 0 }
    }
  }
  let post_ready = match(post.poll()) { Pending => 0, Ready(_) => 1,
  }

  let mut pending_remaining = 1
  let mut condition_checks = 0
  let pending_ptr = ptr<mut>(borrow<mut>(pending_remaining))
  let checks_ptr = ptr<mut>(borrow<mut>(condition_checks))
  let mut pending = async {
    while(
      unsafe {
      *checks_ptr = *checks_ptr + 1
      *pending_ptr > 0
    }
    ) {
      let ignored = await(pending_step(pending_ptr))
    }
  }
  let was_pending = match(pending.poll()) { Pending => 1, Ready(_) => 0,
  }
  let became_ready = match(pending.poll()) { Pending => 0, Ready(_) => 1,
  }

  31 + pre_ready + false_ready + post_ready + unsafe { *polls_ptr } +
    was_pending + became_ready + unsafe { *checks_ptr }
}

test("async_await_recurring_while.sc") {
  std.test.assert(main() == 42)
}
