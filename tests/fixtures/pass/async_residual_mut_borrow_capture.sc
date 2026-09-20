let Future = core.async.Future
let Poll = core.async.Poll

let ask = effect {
  ask (): i32
}

let request = { with<ask>(): i32 =>
  ask.ask()
}

let poll_once = { <e: effects, f: type, t: type>with<e>(future: Borrow<mut><f>): Poll<t> requires(f is Future<e> && f.Output == t) =>
  future.poll()
}

let program = { (value: Borrow<mut><i32>): i32 =>
  let mut future = async {
    let amount = request()
    value = value + amount
    value
  }
  ask.handle(do {
      let polled: Poll<i32> = poll_once(future)
      match(polled) { Ready(result) => result, Pending => 0,
      }
    }) {
    ask(resume) => do { resume(40) },
  }
}

let main = { (): i32 =>
  let mut value = 2
  program(value)
}

test("async_residual_mut_borrow_capture.sc") {
  std.test.assert(main() == 42)
}
