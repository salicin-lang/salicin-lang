let Executor = core.async.Executor
let Future = core.async.Future
let Poll = core.async.Poll
let Spin = std.async.Spin

let step = struct {
  polled: bool
}

extend<step, Future<()>> {
  let Output = i32;

  let poll<r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> = {
    if(self.polled) {
      Poll<i32>.Ready(41)
    } else: {
      self.polled = true
      Poll<i32>.Pending
    }
  }
}

let main(): i32 = {
  let mut executor = Spin {}
  let pending = step { polled: false }
  let ready = async { 1 }
  let first: i32 = executor.run(pending)
  let second: i32 = executor.run(ready)
  first + second
}

test<"async_spin_executor.sc"> {
  std.test.assert(main() == 42)
}
