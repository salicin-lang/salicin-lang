let IntoIterator = core.iter.IntoIterator

let iterable = struct {}
let iter = struct {}

extend<iterable, IntoIterator> {
  let Iter = iter;
  let into_iter(move self)
    (): iter = { iter {}
  }
}

let main(): i32 = {
  for iterable {} { value =>
    value
  }
  0
}
