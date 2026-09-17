let Poll = core.async.Poll
let Future = core.async.Future

let marker = struct {
  drops: Ptr<mut><i32>
}

extend(marker, Droppable) {
  let drop(self: Borrow<mut><self>)(): () = {
    unsafe {
      *self.drops = *self.drops + 1
    }
  }
}

let step = struct {
  drops: Ptr<mut><i32>
}

extend(step, Future<()>) {
  let Output = marker;

  let poll<r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<marker> = {
    Poll<marker>.Ready(marker{ drops: self.drops })
  }
}

let step(drops: Ptr<mut><i32>): step = {
  step{ drops: drops }
}

let main(): i32 = {
  let mut remaining = 2
  let mut drops = 0
  let remaining_ptr = ptr<mut>(borrow<mut>(remaining))
  let drops_ptr = ptr<mut>(borrow<mut>(drops))
  let mut future = async {
    loop {
      let marker = await step(drops_ptr)
      if unsafe {
        *remaining_ptr = *remaining_ptr - 1
        *remaining_ptr == 0
      } {
        break(marker)
      } else {
        continue()
      }
    }
  }

  match future.poll()
    { Pending -> () }
    { Ready(marker) -> () }
  40 + unsafe { *drops_ptr }
}

test("async_await_loop_value_move.sc") {
  std.test.assert(main() == 42)
}
