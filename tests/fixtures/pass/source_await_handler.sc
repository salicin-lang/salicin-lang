let suspension = core.async.suspension
let Poll = core.async.Poll
let Future = core.async.Future
let await_source = core.async.await

let step = struct { ready: bool }

extend(step, Future<()>) {
  let Output = i32;

  let poll = { <r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> =>
    if(self.ready) {
      Ready(42)
    } else: {
      self.ready = true
      Pending
    }
  }
}

let main = {
  (): i32 =>
  suspension.handle {
    suspend: { (resume) => resume(()) },
    action: { await_source(step { ready: false }) },
  }
}

test("source_await_handler.sc") {
  std.test.assert(main() == 42)
}
