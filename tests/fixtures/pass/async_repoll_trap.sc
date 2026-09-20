let Future = core.async.Future

let main = { (): i32 =>
  let mut future = async { 42 }
  let first = future.poll()
  let second = future.poll()
  0
}

test("async_repoll_trap.sc") {
  std.test.assert(main() == 42)
}
