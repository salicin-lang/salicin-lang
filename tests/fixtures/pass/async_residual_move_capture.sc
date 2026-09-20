let Future = core.async.Future
let Poll = core.async.Poll

let ask = effect {
  let ask = (): i32
}

let resource = struct {
  value: i32,
  drops: Ptr<mut><i32>
  }

extend(resource, Droppable) {
  let drop = (self: Borrow<mut><self>)(): () => {
    unsafe {
      *self.drops = *self.drops + 1
    }
  }
}

let request = with<ask>(): i32 => {
  ask.ask()
}

let consume = (move resource: resource): i32 => {
  resource.value
}

let poll_once = <e: effects, f: type, t: type>with<e>(future: Borrow<mut><f>): Poll<t> requires(f is Future<e> && f.Output == t) => {
  future.poll()
}

let main = (): i32 => {
  let drops = unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
  unsafe {
    *drops = 0
  }
  let resource = resource { value: 2, drops: drops }
  let mut future = async {
    consume(resource) + request()
  }
  let result: i32 = ask.handle {
    ask: (resume) => { resume(40) },
    action: {
      let polled: Poll<i32> = poll_once(future)
      match(polled) { Ready(value) => value, Pending => 0,
      }
    },
  }
  let drop_count = unsafe {
    *drops
  }
  unsafe {
    raw_dealloc(drops, size_of<i32>, align_of<i32>)
  }
  if(result == 42 && drop_count == 1) { 42 } else: { 0 }
}

test("async_residual_move_capture.sc") {
  std.test.assert(main() == 42)
}
