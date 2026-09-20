let Option = core.Option
let Result = core.Result
let throwing = core.error.throwing
let Iterator = core.iter.Iterator
let IntoIterator = core.iter.IntoIterator
let OwnedItem = core.iter.OwnedItem

let counter = struct { current: i32, end: i32 }

extend(counter, Iterator) {
  let Item = OwnedItem<i32>;

  let next = { <r: region>(self: Borrow<mut><r><self>)(): Option<i32> =>
    if(self.current < self.end) {
      let value = self.current
      self.current = self.current + 1
      Some(value)
    } else: {
      None
    }
  }
}

extend(counter, IntoIterator) {
  let Iter = counter;

  let into_iter = { (move self)(): counter =>
    self
  }
}

let check = { with<throwing<bool>>(value: i32): () =>
  if(value < 0) { throw(true) } else: { () }
}

let visit = { with<throwing<bool>>(start: i32): i32 =>
  for counter { current: start, end: 4 } { value ->
    check(value)
  }
  42
}

let main = { (): i32 =>
  let success: Result<bool><i32> = try {
    visit(0)
  }
  let failure: Result<bool><i32> = try {
    visit(-1)
  }
  (success ?? 0) + (failure ?? 0)
}

test("for_failure.sc") {
  std.test.assert(main() == 42)
}
