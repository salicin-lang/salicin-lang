let Option = core.Option

let Iterator = core.iter.Iterator
let IntoIterator = core.iter.IntoIterator
let OwnedItem = core.iter.OwnedItem

let counter = struct { current: i32, end: i32 }

extend(counter, Iterator) {
  let Item = OwnedItem<i32>;

  let next = <r: region>(self: Borrow<mut><r><self>)(): Option<i32> => {
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
  let into_iter = (move self)(): counter => { self }}

let main = (): i32 => {
  let mut total = 21
  for counter { current: 0, end: 7 } { value ->
    total = total + value
  }
  total
}

test("for_iterator.sc") {
  std.test.assert(main() == 42)
}
