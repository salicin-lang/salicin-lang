let Poll = core.async.Poll
let Future = core.async.Future

let child() = {
  async { 1 }
}

let main(): i32 = {
  let mut future = async {
    let first = 41
    let copy = first
    let second = await(child())
    copy + second
  }
  match(future.poll()) { Ready(value) => value, Pending => 0,
  }
}

test("async_await_retains_local.sc") {
  std.test.assert(main() == 42)
}
