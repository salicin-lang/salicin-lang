let Option = core.Option

let Iterator = core.iter.Iterator
let IntoIterator = core.iter.IntoIterator
let OwnedItem = core.iter.OwnedItem

let once = struct { done: bool }

extend<once, Iterator> {
  let Item = OwnedItem<i32>;
  let next: <r: region>(self: Borrow<mut><r><self>)
    (): Option<i32> = {
    if(self.done) {
      None
    } else: {
      self.done = true
      Some(1)
    }
  }
}

extend<once, IntoIterator> {
  let Iter = once;
  let into_iter: (move self)(): once = { self }}

let main: (): i32 = {
  for once { done: false } { value =>
    break(value)
  }
  0
}
