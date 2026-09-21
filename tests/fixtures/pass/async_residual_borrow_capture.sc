let Future = core.async.Future
let Poll = core.async.Poll

let ask = effect {
  ask: (): i32
}

let request = { with<ask>
  (): i32 =>
  ask.ask()
}

let poll_once: <e: effects, f: type, t: type> = { with<e>
  (future: Borrow<mut><f>): Poll<t> requires(f is Future<e> && f.Output == t) =>
  future.poll()
}

let program = {
  (offset: Borrow<i32>): i32 =>
  let mut future = async {
    request() + offset
  }
  ask.handle {
    ask: { (resume) => resume(40) },
    action: { let polled: Poll<i32> = poll_once(future)
      match(polled) { Ready(value) => value, Pending => 0,
      }
    },
  }
}

let main = {
  (): i32 =>
  let offset = 2
  program(offset)
}

test("async_residual_borrow_capture.sc") {
  std.test.assert(main() == 42)
}
