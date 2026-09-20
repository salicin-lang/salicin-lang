let Future = core.async.Future
let Poll = core.async.Poll

let ask = effect {
  ask (): bool
}

let step = struct {
  done: bool,
}

extend(step, Future<()>) {
  let Output = bool;

  let poll = { <r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<bool> =>
    Poll<bool>.Ready(self.done)
  }
}

let make_step = { with<ask>(): step =>
  step{ done: ask.ask() }
}

let main = { (): i32 =>
  ask.handle(do {
      let mut future = async {
        loop {
          let first = await(make_step())
          let second = await(make_step())
          if(second) {
            break(42)
          } else: {
            continue()
          }
        }
      }
      match(future.poll()) { Ready(value) => value, Pending => 0,
      }
    }) {
    ask(resume) => do { resume(false) },
  }
}
