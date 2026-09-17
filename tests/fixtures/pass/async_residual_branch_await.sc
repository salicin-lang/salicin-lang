let Future = core.async.Future
let Poll = core.async.Poll

let ask = effect {
  let ask(): i32
}

let step = struct {
  drops: Ptr<mut><i32>,
  polls: i32,
  value: i32,
  drop_amount: i32,
}

extend(step, Droppable) {
  let drop(self: Borrow<mut><self>)(): () = {
    unsafe {
      *self.drops = *self.drops + self.drop_amount
    }
  }
}

extend(step, Future(())) {
  let Output = i32;

  let poll<r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> = {
    if self.polls == 0 {
      self.polls = 1
      Poll<i32>.Pending
    } else {
      Poll<i32>.Ready(self.value)
    }
  }
}

let run(drops: Ptr<mut><i32>, first: bool): i32 = {
  let mut future = async {
    if first {
      await step{ drops: drops, polls: 0, value: ask.ask(), drop_amount: 10 }
    } else {
      await step{ drops: drops, polls: 0, value: ask.ask(), drop_amount: 1 }
    }
  }
  ask.handle ask { (resume) -> resume(40) } action {
      let pending = future.poll()
      let ready = future.poll()
      match pending
        { Pending -> match ready
          { Ready(value) -> value }
          { Pending -> 0 } }
        { Ready(_) -> 0 }
    }
}

let cancel_second(drops: Ptr<mut><i32>): i32 = {
  ask.handle ask { (resume) -> resume(40) } action {
      let mut future = async {
        if false {
          await step{ drops: drops, polls: 0, value: ask.ask(), drop_amount: 10 }
        } else {
          await step{ drops: drops, polls: 0, value: ask.ask(), drop_amount: 1 }
        }
      }
      match future.poll()
        { Pending -> 42 }
        { Ready(_) -> 0 }
    }
}

let main(): i32 = {
  let drops = unsafe {
    raw_alloc(i32)(size_of<i32>, align_of<i32>)
  }
  unsafe {
    *drops = 0
  }

  let first = run(drops, true)
  let second = run(drops, false)
  let cancelled = cancel_second(drops)
  let drop_count = unsafe {
    *drops
  }
  unsafe {
    raw_dealloc(drops, size_of<i32>, align_of<i32>)
  }

  if first == 40 && second == 40 && cancelled == 42 && drop_count == 12 {
    42
  } else {
    0
  }
}

test("async_residual_branch_await.sc") {
  std.test.assert(main() == 42)
}
