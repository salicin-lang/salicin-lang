let Option = core.Option
let Iterator = core.iter.Iterator
let IntoIterator = core.iter.IntoIterator
let OwnedItem = core.iter.OwnedItem

let values = struct { done: bool }
let choice = enum { Some(i32), None }

extend(values, Iterator) {
  let Item = OwnedItem<choice>;

  let next: <r: region>(self: Borrow<mut><r><self>)
    (): Option<choice> = {
    if(self.done) {
      None
    } else: {
      self.done = true
      Some(choice.Some(42))
    }
  }
}

extend(values, IntoIterator) {
  let Iter = values;
  let into_iter: (move self)(): values = { self }
}

let main: (): i32 = {
  for values { done: false } { choice.Some(value) =>
    value
  }
  0
}
