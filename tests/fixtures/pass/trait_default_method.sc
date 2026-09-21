let read = trait {
  read(self: Borrow<self>)(): i32;
  doubled(self: Borrow<self>)(): i32 = self.read() + self.read()
}

let number = struct { value: i32 }

extend<number, read> {
  let read(self: Borrow<self>)(): i32 = { self.value }
}

let override = struct {}

extend<override, read> {
  let read(self: Borrow<self>)(): i32 = { 0 }
  let doubled(self: Borrow<self>)(): i32 = { 42 }
}

let cell<t: type> = struct { value: t }

extend<cell<t>, read><requires: t is read> {
  let read(self: Borrow<self>)(): i32 = { self.value.read() }
}

let take = trait {
  Item: type
  take(move self)(): Item;
  forward(move self)(): Item = self.take()
}

let boxed = struct { value: i32 }

extend<boxed, take> {
  let Item = i32;
  let take(move self)(): i32 = { self.value }
}

let main(): i32 = {
  let number = number { value: 21 }
  let cell = cell { value: number }
  let overridden = override {}
  let boxed = boxed { value: 42 }
  cell.doubled() + overridden.doubled() + boxed.forward() - 84
}

test<"trait_default_method.sc"> {
  std.test.assert(main() == 42)
}
