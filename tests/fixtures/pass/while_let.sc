let Option = core.Option

let counter = struct { current: i32, end: i32 }

extend(counter) {
  let next(self: Borrow<mut><self>)(): Option<i32> = {
    if self.current < self.end {
      let value = self.current
      self.current = self.current + 1
      Some(value)
    } else {
      None
    }
  }
}

let main(): i32 = {
  let mut counter = counter{ current: 0, end: 7 }
  let mut total = 24
  loop {
    match counter.next()
      { Some(value) ->
        if value < 3 {
          continue()
        }
        total = total + value
      }
      { None -> break() }
  }
  total
}

test("while_let.sc") {
  std.test.assert(main() == 42)
}
