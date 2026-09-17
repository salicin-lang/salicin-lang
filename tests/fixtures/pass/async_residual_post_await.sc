let Future = core.async.Future
let Poll = core.async.Poll

let ask = effect {
  let ask(): i32
}

let step = struct {
  polls: i32,
  value: i32,
}

extend(step, Future<()>) {
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

let make_step: with<ask>(): step = {
  step{ polls: 0, value: ask.ask() }
}

let main(): i32 = {
  let mut future = async {
    let value = await make_step()
    value + 2
  }
  ask.handle ask { (resume) -> resume(40) } action {
      let first = future.poll()
      let second = future.poll()
      match first
        { Pending -> match second
          { Ready(value) -> value }
          { Pending -> 0 } }
        { Ready(_) -> 0 }
    }
}

test("async_residual_post_await.sc") {
  std.test.assert(main() == 42)
}
