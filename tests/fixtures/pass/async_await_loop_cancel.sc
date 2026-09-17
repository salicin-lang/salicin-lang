let Poll = core.async.Poll
let Future = core.async.Future

let step = struct {
  polls: Ptr<mut><i32>,
  drops: Ptr<mut><i32>
}

extend(step, Droppable) {
  let drop(self: Borrow<mut><self>)(): () = {
    unsafe {
      *self.drops = *self.drops + 1
    }
  }
}

extend(step, Future(())) {
  let Output = bool;

  let poll<r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<bool> = {
    unsafe {
      if *self.polls == 0 {
        *self.polls = 1
        Poll<bool>.Ready(false)
      } else {
        Poll<bool>.Pending
      }
    }
  }
}

let step(polls: Ptr<mut><i32>, drops: Ptr<mut><i32>): step = {
  step{ polls: polls, drops: drops }
}

let main(): i32 = {
  let mut polls = 0
  let mut drops = 0
  let polls_ptr = ptr<mut>(borrow<mut>(polls))
  let drops_ptr = ptr<mut>(borrow<mut>(drops))

  let pending = do {
    let mut future = async {
      loop {
        let done = await step(polls_ptr, drops_ptr)
        if done {
          break()
        } else {
          continue()
        }
      }
    }
    match future.poll()
      { Pending -> 1 }
      { Ready(_) -> 0 }
  }

  39 + pending + unsafe { *drops_ptr }
}

test("async_await_loop_cancel.sc") {
  std.test.assert(main() == 42)
}
