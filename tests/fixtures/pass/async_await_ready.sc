let Poll = core.async.Poll
let Future = core.async.Future

let main = { (): i32 =>
  let mut future = async {
    await(async { 42 })
  }
  match(future.poll()) { Ready(value) => value, Pending => 0,
  }
}

test("async_await_ready.sc") {
  std.test.assert(main() == 42)
}
