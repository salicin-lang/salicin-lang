let Poll = core.async.Poll
let Future = core.async.Future

let child() = {
  async { 1 }
}

let main(): i32 = {
  let value = 41
  let reference: Borrow<i32> = borrow(value)
  let mut future = async {
    let awaited = await child()
    reference + awaited
  }
  match(future.poll()) { Ready(result) => result, Pending => 0,
  }
}

test("async_await_external_borrow.sc") {
  std.test.assert(main() == 42)
}
